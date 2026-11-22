package com.g41us.faithhymn

import android.app.Application
import com.g41us.faithhymn.audio.AudioStore
import com.g41us.faithhymn.audio.HymnPlayer
import com.g41us.faithhymn.data.HymnRepository
import com.g41us.faithhymn.data.SettingsStore
import com.g41us.faithhymn.update.Updater

class FaithHymnApp : Application() {
    override fun onCreate() { super.onCreate(); com.g41us.faithhymn.ui.CanvasFonts.init(this) }

    val repo by lazy { HymnRepository(this) }
    val settings by lazy { SettingsStore(this) }
    val audio by lazy { AudioStore(this) }
    val player by lazy { HymnPlayer(this, audio) }
    val updater by lazy { Updater(this) }
    val catalog by lazy { com.g41us.faithhymn.audio.AudioCatalog(this) }
}
