package com.g41us.faithhymn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.ui.AppRoot
import com.g41us.faithhymn.ui.FaithHymnTheme
import com.g41us.faithhymn.ui.resolveDark
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Incoming faithhymns://playlist links (shared playlists / QR codes). */
    val incomingLink = MutableStateFlow<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as FaithHymnApp
        incomingLink.value = intent?.data
        setContent {
            val s by app.settings.settings.collectAsStateWithLifecycle()
            val dark = resolveDark(s.theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            FaithHymnTheme(s.theme, s.accent) { AppRoot(app, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { incomingLink.value = it }
    }
}
