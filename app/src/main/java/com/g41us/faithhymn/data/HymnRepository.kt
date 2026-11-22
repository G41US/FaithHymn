package com.g41us.faithhymn.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Read-only hymn book (bundled in assets) + writable user data (favourites, history, playlists). */
class HymnRepository(private val ctx: Context) {

    private val hymnDb: SQLiteDatabase by lazy { openBundledDb() }
    private val user = UserDb(ctx)

    /** Bumped on every change to user data so screens can refresh. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes
    private fun changed() { _changes.value++ }

    private fun openBundledDb(): SQLiteDatabase {
        val file = ctx.getDatabasePath("hymns.db")
        val stamp = File(file.parentFile, "hymns.version")
        val wanted = BUNDLED_VERSION.toString()
        if (!file.exists() || stamp.takeIf { it.exists() }?.readText() != wanted) {
            file.parentFile?.mkdirs()
            ctx.assets.open("hymns.db").use { input -> file.outputStream().use { input.copyTo(it) } }
            stamp.writeText(wanted)
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    suspend fun all(): List<HymnSummary> = io {
        hymnDb.rawQuery("SELECT number,title,first_line FROM hymns ORDER BY number", null).use { c ->
            buildList { while (c.moveToNext()) add(HymnSummary(c.getInt(0), c.getString(1), c.getString(2))) }
        }
    }

    suspend fun hymn(number: Int): Hymn? = io {
        hymnDb.rawQuery("SELECT number,title,content,first_line FROM hymns WHERE number=?", arrayOf("$number")).use { c ->
            if (c.moveToFirst()) Hymn(c.getInt(0), c.getString(1), c.getString(2), c.getString(3)) else null
        }
    }

    val count: Int get() = 1001

    /** Number lookup, then full-text search across titles and lyrics (prefix match on every word). */
    suspend fun search(query: String): List<HymnSummary> = io {
        val q = query.trim()
        if (q.isEmpty()) return@io emptyList()
        val results = mutableListOf<HymnSummary>()
        q.toIntOrNull()?.let { n -> hymnSummary(n)?.let(results::add) }
        val words = Regex("[\\p{L}\\p{N}']+").findAll(q).map { it.value.replace("'", "") }
            .filter { it.isNotEmpty() }.toList()
        if (words.isNotEmpty()) {
            val match = words.joinToString(" ") { "$it*" }
            val sql = """
                SELECT h.number, h.title, h.first_line,
                       snippet(hymns_fts, '[', ']', '…', 1, 12)
                FROM hymns_fts JOIN hymns h ON h.number = hymns_fts.docid
                WHERE hymns_fts MATCH ? LIMIT 200"""
            hymnDb.rawQuery(sql, arrayOf(match)).use { c ->
                val rows = buildList {
                    while (c.moveToNext()) add(HymnSummary(c.getInt(0), c.getString(1), c.getString(2), c.getString(3)))
                }
                // Title hits first, then lyric hits.
                val lower = words.map { it.lowercase() }
                rows.sortedByDescending { r -> lower.count { r.title.lowercase().contains(it) } }
                    .filter { r -> results.none { it.number == r.number } }
                    .let(results::addAll)
            }
        }
        results
    }

    private fun hymnSummary(n: Int): HymnSummary? =
        hymnDb.rawQuery("SELECT number,title,first_line FROM hymns WHERE number=?", arrayOf("$n")).use { c ->
            if (c.moveToFirst()) HymnSummary(c.getInt(0), c.getString(1), c.getString(2)) else null
        }

    /** Same hymn all day, changes daily. */
    suspend fun hymnOfTheDay(): HymnSummary? = io {
        val day = LocalDate.now().toEpochDay()
        hymnSummary(((day * 7919) % count + 1).toInt())
    }

    // ---- favourites ----
    suspend fun isFavourite(n: Int) = io { user.exists("SELECT 1 FROM favourites WHERE number=?", n) }
    suspend fun toggleFavourite(n: Int) = io {
        val w = user.writableDatabase
        if (w.delete("favourites", "number=?", arrayOf("$n")) == 0)
            w.insert("favourites", null, ContentValues().apply { put("number", n); put("at", now()) })
        changed()
    }
    suspend fun favourites(): List<HymnSummary> = io { summaries(user.ints("SELECT number FROM favourites ORDER BY at DESC")) }

    // ---- history ----
    suspend fun markOpened(n: Int) = io {
        user.writableDatabase.insertWithOnConflict("history", null,
            ContentValues().apply { put("number", n); put("at", now()) }, SQLiteDatabase.CONFLICT_REPLACE)
        user.writableDatabase.execSQL("DELETE FROM history WHERE number NOT IN (SELECT number FROM history ORDER BY at DESC LIMIT 50)")
        changed()
    }
    suspend fun recent(limit: Int = 20): List<HymnSummary> =
        io { summaries(user.ints("SELECT number FROM history ORDER BY at DESC LIMIT $limit")) }
    suspend fun clearHistory() = io { user.writableDatabase.delete("history", null, null); changed() }

    // ---- playlists ----
    suspend fun playlists(): List<Playlist> = io {
        user.readableDatabase.rawQuery(
            "SELECT p.id,p.name,(SELECT COUNT(*) FROM playlist_items i WHERE i.playlist_id=p.id) FROM playlists p ORDER BY p.id DESC", null
        ).use { c -> buildList { while (c.moveToNext()) add(Playlist(c.getLong(0), c.getString(1), c.getInt(2))) } }
    }
    suspend fun playlistName(id: Long): String? = io {
        user.readableDatabase.rawQuery("SELECT name FROM playlists WHERE id=?", arrayOf("$id")).use { if (it.moveToFirst()) it.getString(0) else null }
    }
    suspend fun createPlaylist(name: String): Long = io {
        user.writableDatabase.insert("playlists", null, ContentValues().apply { put("name", name.trim()) }).also { changed() }
    }
    suspend fun renamePlaylist(id: Long, name: String) = io {
        user.writableDatabase.update("playlists", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf("$id")); changed()
    }
    suspend fun deletePlaylist(id: Long) = io {
        user.writableDatabase.delete("playlist_items", "playlist_id=?", arrayOf("$id"))
        user.writableDatabase.delete("playlists", "id=?", arrayOf("$id")); changed()
    }
    suspend fun addToPlaylist(id: Long, n: Int) = io {
        val pos = user.ints("SELECT IFNULL(MAX(pos),0)+1 FROM playlist_items WHERE playlist_id=$id").first()
        user.writableDatabase.insertWithOnConflict("playlist_items", null,
            ContentValues().apply { put("playlist_id", id); put("number", n); put("pos", pos) }, SQLiteDatabase.CONFLICT_IGNORE)
        changed()
    }
    suspend fun removeFromPlaylist(id: Long, n: Int) = io {
        user.writableDatabase.delete("playlist_items", "playlist_id=? AND number=?", arrayOf("$id", "$n")); changed()
    }
    suspend fun moveInPlaylist(id: Long, n: Int, up: Boolean) = io {
        val order = user.ints("SELECT number FROM playlist_items WHERE playlist_id=$id ORDER BY pos").toMutableList()
        val i = order.indexOf(n); val j = if (up) i - 1 else i + 1
        if (i >= 0 && j in order.indices) {
            java.util.Collections.swap(order, i, j)
            val w = user.writableDatabase
            w.beginTransaction()
            try {
                order.forEachIndexed { p, num -> w.execSQL("UPDATE playlist_items SET pos=? WHERE playlist_id=? AND number=?", arrayOf(p, id, num)) }
                w.setTransactionSuccessful()
            } finally { w.endTransaction() }
            changed()
        }
    }
    suspend fun playlistHymns(id: Long): List<HymnSummary> =
        io { summaries(user.ints("SELECT number FROM playlist_items WHERE playlist_id=$id ORDER BY pos")) }

    private fun summaries(numbers: List<Int>): List<HymnSummary> = numbers.mapNotNull { hymnSummary(it) }
    private fun now() = System.currentTimeMillis()
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object { const val BUNDLED_VERSION = 1 }
}

private class UserDb(ctx: Context) : SQLiteOpenHelper(ctx, "user.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favourites(number INTEGER PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(number INTEGER PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE playlist_items(playlist_id INTEGER NOT NULL, number INTEGER NOT NULL, pos INTEGER NOT NULL, PRIMARY KEY(playlist_id, number))")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    fun exists(sql: String, arg: Int) = readableDatabase.rawQuery(sql, arrayOf("$arg")).use { it.moveToFirst() }
    fun ints(sql: String): List<Int> = readableDatabase.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }
}
