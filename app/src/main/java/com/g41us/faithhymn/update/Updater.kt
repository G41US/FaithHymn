package com.g41us.faithhymn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.g41us.faithhymn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Checks GitHub Releases for a newer signed APK, downloads it and hands it to the system installer. */
class Updater(private val ctx: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private val dir = File(ctx.cacheDir, "updates")

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    suspend fun check() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val r = latest()
            when {
                r == null -> UpdateState.UpToDate
                r.versionCode > currentVersionCode -> UpdateState.Available(r)
                else -> UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Error("Couldn't check for updates. Check your internet connection.")
        }
    }

    private suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val c = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=15").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15000; readTimeout = 15000
        }
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") && it.optString("tag_name").startsWith("v") }
            .mapNotNull { rel ->
                val tag = rel.getString("tag_name").removePrefix("v")
                val code = tag.substringAfterLast('.').toIntOrNull() ?: return@mapNotNull null
                val assets = rel.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
                    ?: return@mapNotNull null
                Release(code, tag, apk.getString("browser_download_url"), apk.optLong("size"), rel.optString("body").trim())
            }
            .maxByOrNull { it.versionCode }
    }

    suspend fun download(r: Release) {
        _state.value = UpdateState.Downloading(r, 0f)
        _state.value = try {
            withContext(Dispatchers.IO) {
                dir.mkdirs(); dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "FaithHymns-${r.versionName}.apk")
                val c = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 15000; readTimeout = 30000 }
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024); var done = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) _state.value = UpdateState.Downloading(r, done.toFloat() / total)
                        }
                    }
                }
                UpdateState.ReadyToInstall(r, out)
            }
        } catch (e: Exception) {
            UpdateState.Error("Download failed. Please try again.")
        }
    }

    /** Android 8+ needs "Install unknown apps" allowed for FaithHymns once. */
    fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
