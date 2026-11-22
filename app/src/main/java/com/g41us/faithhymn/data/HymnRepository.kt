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

/** Read-only hymn book (bundled in assets) + writable user data (favourites, history, playlists, searches). */
class HymnRepository(private val ctx: Context) {

    private val hymnDb: SQLiteDatabase by lazy { openBundledDb() }
    private val user = UserDb(ctx)

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

    val count: Int get() = 1001

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

    private fun hymnSummary(n: Int): HymnSummary? =
        hymnDb.rawQuery("SELECT number,title,first_line FROM hymns WHERE number=?", arrayOf("$n")).use { c ->
            if (c.moveToFirst()) HymnSummary(c.getInt(0), c.getString(1), c.getString(2)) else null
        }

    // ---------------- search ----------------

    /** Every word used in the book, for spelling correction. */
    private val vocabulary: Map<String, Int> by lazy {
        val v = HashMap<String, Int>()
        hymnDb.rawQuery("SELECT title || ' ' || content FROM hymns", null).use { c ->
            while (c.moveToNext()) Regex("[\\p{L}’']+").findAll(c.getString(0).lowercase()).forEach { val w = it.value.replace("’", "").replace("'", ""); v[w] = (v[w] ?: 0) + 1 }
        }
        v
    }

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResults = io {
        val q = query.trim()
        if (q.isEmpty()) return@io SearchResults(emptyList(), emptyList())
        val number = q.toIntOrNull()
        if (filter == SearchFilter.NUMBER || (number != null && q.all { it.isDigit() })) {
            val hits = if (number != null) listOfNotNull(hymnSummary(number)) +
                (if (filter == SearchFilter.NUMBER) (1..count).filter { it != number && it.toString().startsWith(q) }.take(30).mapNotNull { hymnSummary(it) } else emptyList())
                else emptyList()
            return@io SearchResults(hits.map { it.copy(titleHit = true) }, emptyList())
        }
        val words = words(q)
        if (words.isEmpty()) return@io SearchResults(emptyList(), emptyList())
        var res = ftsSearch(words, filter)
        var corrected: String? = null
        if (res.all.size < 3) {
            val fixed = words.map { w -> correct(w) }
            if (fixed != words) {
                val alt = ftsSearch(fixed, filter)
                if (alt.all.size > res.all.size) { res = alt; corrected = fixed.joinToString(" ") }
            }
        }
        res.copy(correctedQuery = corrected)
    }

    private fun words(q: String) = Regex("[\\p{L}\\p{N}’']+").findAll(q.lowercase()).map { it.value.replace("’", "").replace("'", "") }.filter { it.isNotEmpty() }.toList()

    /** British/American and old spellings found in hymnals, searched both ways. */
    private val variants = listOf("our" to "or", "ise" to "ize", "re" to "er", "ll" to "l")
    private fun spellings(w: String): List<String> {
        val out = linkedSetOf(w)
        for ((a, b) in variants) {
            if (w.endsWith(a) && w.length > a.length + 2) out += w.dropLast(a.length) + b
            if (w.endsWith(b) && w.length > b.length + 2) out += w.dropLast(b.length) + a
        }
        if (w.contains("our")) out += w.replace("our", "or")
        return out.toList()
    }

    private fun ftsSearch(words: List<String>, filter: SearchFilter): SearchResults {
        val col = when (filter) { SearchFilter.TITLE -> "title:"; SearchFilter.LYRICS -> "content:"; else -> "" }
        val match = words.joinToString(" ") { w -> spellings(w).joinToString(" OR ", "(", ")") { "$col$it*" } }
        val sql = """
            SELECT h.number, h.title, h.first_line, snippet(hymns_fts, '[', ']', '…', 1, 10)
            FROM hymns_fts JOIN hymns h ON h.number = hymns_fts.docid
            WHERE hymns_fts MATCH ? LIMIT 300"""
        val rows = runCatching {
            hymnDb.rawQuery(sql, arrayOf(match)).use { c ->
                buildList { while (c.moveToNext()) add(HymnSummary(c.getInt(0), c.getString(1), c.getString(2), c.getString(3))) }
            }
        }.getOrDefault(emptyList())
        val title = mutableListOf<HymnSummary>(); val lyric = mutableListOf<HymnSummary>()
        for (r in rows) {
            val t = words(r.title)
            val inTitle = words.all { w -> spellings(w).any { s -> t.any { it.startsWith(s) } } }
            if (inTitle && filter != SearchFilter.LYRICS) title += r.copy(titleHit = true, snippet = null)
            else if (filter != SearchFilter.TITLE) lyric += r
        }
        return SearchResults(title.sortedBy { it.number }, lyric)
    }

    /** Closest word in the book (typo tolerance: 1 edit for short words, 2 for long). */
    private fun correct(w: String): String {
        if (w.length < 4 || w in vocabulary || vocabulary.keys.any { it.startsWith(w) }) return w
        val max = if (w.length >= 7) 2 else 1
        // Closest word; ties go to the word used most in the hymn book ("grase" -> "grace", not "grass").
        return vocabulary.entries.asSequence().filter { kotlin.math.abs(it.key.length - w.length) <= max }
            .map { Triple(it.key, lev(it.key, w, max), it.value) }.filter { it.second <= max }
            .sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third }).firstOrNull()?.first ?: w
    }

    private fun lev(a: String, b: String, cap: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i; var rowMin = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin > cap) return cap + 1
            prev = cur
        }
        return prev[b.length]
    }

    suspend fun recentSearches(): List<String> = io {
        user.readableDatabase.rawQuery("SELECT q FROM searches ORDER BY at DESC LIMIT 8", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }
    suspend fun saveSearch(q: String) = io {
        val t = q.trim(); if (t.length < 2) return@io
        user.writableDatabase.insertWithOnConflict("searches", null, ContentValues().apply { put("q", t); put("at", now()) }, SQLiteDatabase.CONFLICT_REPLACE)
        user.writableDatabase.execSQL("DELETE FROM searches WHERE q NOT IN (SELECT q FROM searches ORDER BY at DESC LIMIT 20)")
    }
    suspend fun clearSearches() = io { user.writableDatabase.delete("searches", null, null); Unit }

    // ---------------- topics ----------------
    suspend fun topics(): List<Pair<String, Int>> = io {
        hymnDb.rawQuery("SELECT topic, COUNT(*) FROM topics GROUP BY topic ORDER BY MIN(ord)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) }
        }
    }
    suspend fun topicHymns(topic: String): List<HymnSummary> = io {
        hymnDb.rawQuery("SELECT h.number, h.title, h.first_line FROM topics t JOIN hymns h ON h.number = t.number WHERE t.topic = ? ORDER BY h.number", arrayOf(topic)).use { c ->
            buildList { while (c.moveToNext()) add(HymnSummary(c.getInt(0), c.getString(1), c.getString(2))) }
        }
    }
    suspend fun topicsFor(n: Int): List<String> = io {
        hymnDb.rawQuery("SELECT topic FROM topics WHERE number = ? ORDER BY ord", arrayOf("$n")).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    /** Hymn of the day: same all day, changes daily. */
    suspend fun hymnOfTheDay(): HymnSummary? = io {
        val day = LocalDate.now().toEpochDay()
        hymnSummary(((day * 7919) % count + 1).toInt())
    }

    // ---------------- favourites / history ----------------
    suspend fun isFavourite(n: Int) = io { user.exists("SELECT 1 FROM favourites WHERE number=?", n) }
    suspend fun toggleFavourite(n: Int) = io {
        val w = user.writableDatabase
        if (w.delete("favourites", "number=?", arrayOf("$n")) == 0)
            w.insert("favourites", null, ContentValues().apply { put("number", n); put("at", now()) })
        changed()
    }
    suspend fun removeFavourite(n: Int) = io { user.writableDatabase.delete("favourites", "number=?", arrayOf("$n")); changed() }
    suspend fun favourites(): List<HymnSummary> = io { summaries(user.ints("SELECT number FROM favourites ORDER BY at DESC")) }

    suspend fun markOpened(n: Int) = io {
        user.writableDatabase.insertWithOnConflict("history", null,
            ContentValues().apply { put("number", n); put("at", now()) }, SQLiteDatabase.CONFLICT_REPLACE)
        user.writableDatabase.execSQL("DELETE FROM history WHERE number NOT IN (SELECT number FROM history ORDER BY at DESC LIMIT 50)")
        changed()
    }
    suspend fun recent(limit: Int = 20): List<HymnSummary> = io { summaries(user.ints("SELECT number FROM history ORDER BY at DESC LIMIT $limit")) }
    suspend fun removeRecent(n: Int) = io { user.writableDatabase.delete("history", "number=?", arrayOf("$n")); changed() }
    suspend fun clearHistory() = io { user.writableDatabase.delete("history", null, null); changed() }

    // ---------------- playlists / service planner ----------------
    suspend fun playlists(): List<Playlist> = io {
        user.readableDatabase.rawQuery(
            "SELECT p.id,p.name,(SELECT COUNT(*) FROM playlist_items i WHERE i.playlist_id=p.id),p.service_date,p.color FROM playlists p ORDER BY p.id DESC", null
        ).use { c -> buildList { while (c.moveToNext()) add(Playlist(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getInt(4))) } }
            .map { p -> p.copy(preview = user.ints("SELECT number FROM playlist_items WHERE playlist_id=${p.id} ORDER BY pos LIMIT 3")) }
    }
    suspend fun playlist(id: Long): Playlist? = io {
        user.readableDatabase.rawQuery("SELECT id,name,service_date,color FROM playlists WHERE id=?", arrayOf("$id")).use {
            if (it.moveToFirst()) Playlist(it.getLong(0), it.getString(1), 0, it.getString(2), it.getInt(3)) else null
        }
    }
    suspend fun createPlaylist(name: String, serviceDate: String? = null, hymns: List<Int> = emptyList()): Long = io {
        val color = (user.ints("SELECT COUNT(*) FROM playlists").first()) % 8
        val id = user.writableDatabase.insert("playlists", null, ContentValues().apply { put("name", name.trim()); put("service_date", serviceDate); put("color", color) })
        hymns.forEachIndexed { i, n ->
            user.writableDatabase.insertWithOnConflict("playlist_items", null, ContentValues().apply { put("playlist_id", id); put("number", n); put("pos", i) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        changed(); id
    }
    suspend fun updatePlaylist(id: Long, name: String, serviceDate: String?, color: Int) = io {
        user.writableDatabase.update("playlists", ContentValues().apply { put("name", name.trim()); put("service_date", serviceDate); put("color", color) }, "id=?", arrayOf("$id")); changed()
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
    suspend fun setSlot(id: Long, n: Int, slot: String?) = io {
        user.writableDatabase.update("playlist_items", ContentValues().apply { put("slot", slot) }, "playlist_id=? AND number=?", arrayOf("$id", "$n")); changed()
    }
    suspend fun reorder(id: Long, order: List<Int>) = io {
        val w = user.writableDatabase
        w.beginTransaction()
        try {
            order.forEachIndexed { p, num -> w.execSQL("UPDATE playlist_items SET pos=? WHERE playlist_id=? AND number=?", arrayOf(p, id, num)) }
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        changed()
    }
    suspend fun playlistItems(id: Long): List<PlaylistItem> = io {
        user.readableDatabase.rawQuery("SELECT number, slot FROM playlist_items WHERE playlist_id=? ORDER BY pos", arrayOf("$id")).use { c ->
            buildList { while (c.moveToNext()) { val s = hymnSummary(c.getInt(0)); if (s != null) add(PlaylistItem(s, c.getString(1))) } }
        }
    }
    suspend fun playlistHymns(id: Long): List<HymnSummary> = playlistItems(id).map { it.hymn }

    private fun summaries(numbers: List<Int>): List<HymnSummary> = numbers.mapNotNull { hymnSummary(it) }
    private fun now() = System.currentTimeMillis()
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object { const val BUNDLED_VERSION = 6 }
}

private class UserDb(ctx: Context) : SQLiteOpenHelper(ctx, "user.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favourites(number INTEGER PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE history(number INTEGER PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, service_date TEXT, color INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE playlist_items(playlist_id INTEGER NOT NULL, number INTEGER NOT NULL, pos INTEGER NOT NULL, slot TEXT, PRIMARY KEY(playlist_id, number))")
        db.execSQL("CREATE TABLE searches(q TEXT PRIMARY KEY, at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN service_date TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN slot TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS searches(q TEXT PRIMARY KEY, at INTEGER NOT NULL)")
        }
    }
    fun exists(sql: String, arg: Int) = readableDatabase.rawQuery(sql, arrayOf("$arg")).use { it.moveToFirst() }
    fun ints(sql: String): List<Int> = readableDatabase.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }
}
