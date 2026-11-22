package com.g41us.faithhymn.audio

import android.content.Context
import android.net.Uri
import com.g41us.faithhymn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where each hymn's MP3 lives: downloaded copy first, otherwise streamed from AUDIO_BASE_URL. */
class AudioStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "audio").apply { mkdirs() }

    /** hymn number -> download progress 0..1 while downloading. */
    private val _progress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val progress: StateFlow<Map<Int, Float>> = _progress
    private val _downloaded = MutableStateFlow(scan())
    val downloaded: StateFlow<Set<Int>> = _downloaded

    fun remoteUrl(n: Int) = "${BuildConfig.AUDIO_BASE_URL}$n.mp3"
    private fun file(n: Int) = File(dir, "$n.mp3")
    fun uri(n: Int): Uri = file(n).takeIf { it.exists() }?.let(Uri::fromFile) ?: Uri.parse(remoteUrl(n))
    fun isDownloaded(n: Int) = file(n).exists()
    private fun scan() = dir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".mp3").toIntOrNull() }.toSet()
    fun totalBytes() = dir.listFiles().orEmpty().sumOf { it.length() }

    sealed interface Result { data object Ok : Result; data object NotFound : Result; data class Failed(val msg: String) : Result }

    suspend fun download(n: Int): Result = withContext(Dispatchers.IO) {
        val tmp = File(dir, "$n.part")
        try {
            _progress.value = _progress.value + (n to 0f)
            val c = (URL(remoteUrl(n)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            }
            when (c.responseCode) {
                404, 403 -> return@withContext Result.NotFound
                in 200..299 -> Unit
                else -> return@withContext Result.Failed("Server returned ${c.responseCode}")
            }
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024); var read = 0L
                    while (true) {
                        val r = input.read(buf); if (r < 0) break
                        out.write(buf, 0, r); read += r
                        if (total > 0) _progress.value = _progress.value + (n to read.toFloat() / total)
                    }
                }
            }
            tmp.renameTo(file(n))
            _downloaded.value = _downloaded.value + n
            Result.Ok
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Network error")
        } finally {
            tmp.delete()
            _progress.value = _progress.value - n
        }
    }

    fun delete(n: Int) { file(n).delete(); _downloaded.value = _downloaded.value - n }
    fun deleteAll() { dir.listFiles()?.forEach { it.delete() }; _downloaded.value = emptySet() }
}
