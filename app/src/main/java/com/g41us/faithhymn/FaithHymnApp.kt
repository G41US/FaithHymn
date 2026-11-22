package com.g41us.faithhymn

import android.app.Application
import com.g41us.faithhymn.audio.AudioStore
import com.g41us.faithhymn.audio.HymnPlayer
import com.g41us.faithhymn.data.HymnRepository
import com.g41us.faithhymn.data.SettingsStore

class FaithHymnApp : Application() {
    val repo by lazy { HymnRepository(this) }
    val settings by lazy { SettingsStore(this) }
    val audio by lazy { AudioStore(this) }
    val player by lazy { HymnPlayer(this, audio) }
}
