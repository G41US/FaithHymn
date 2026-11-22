package com.g41us.faithhymn.audio

import android.content.Context
import com.g41us.faithhymn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Which hymns have a recording: the MP3 names in the GitHub "audio" release. Cached for offline use. */
class AudioCatalog(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("audio_catalog", Context.MODE_PRIVATE)
    private val _available = MutableStateFlow(prefs.getString("list", "")!!.split(",").mapNotNull { it.toIntOrNull() }.toSet())
    val available: StateFlow<Set<Int>> = _available

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - prefs.getLong("at", 0) < 6 * 3600_000 && _available.value.isNotEmpty()) return@withContext
        runCatching {
            fun get(url: String): String? {
                val c = (URL(url).openConnection() as HttpURLConnection)
                    .apply { connectTimeout = 10000; readTimeout = 15000; setRequestProperty("Accept", "application/vnd.github+json") }
                return if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            }
            val nums = mutableSetOf<Int>()
            val rel = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/audio")
            if (rel != null) {
                val id = JSONObject(rel).getLong("id")
                for (page in 1..12) {
                    val arr = org.json.JSONArray(get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/$id/assets?per_page=100&page=$page") ?: break)
                    for (i in 0 until arr.length()) arr.getJSONObject(i).getString("name").removeSuffix(".mp3").toIntOrNull()?.let(nums::add)
                    if (arr.length() < 100) break
                }
            }
            _available.value = nums
            prefs.edit().putString("list", nums.joinToString(",")).putLong("at", System.currentTimeMillis()).apply()
        }
    }
}
