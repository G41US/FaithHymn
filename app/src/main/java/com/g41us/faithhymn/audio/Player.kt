package com.g41us.faithhymn.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.g41us.faithhymn.data.HymnSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val number: Int? = null,
    val title: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
)

/** App-side handle to the PlaybackService. */
class HymnPlayer(private val ctx: Context, private val audio: AudioStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private suspend fun ctrl(): MediaController {
        controller?.let { return it }
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val c = MediaController.Builder(ctx, token).buildAsync().await()
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
            override fun onPlayerError(error: PlaybackException) {
                val code = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                _state.value = _state.value.copy(
                    playing = false, buffering = false,
                    error = if (code == 404 || code == 403) "No recording for this hymn yet" else "Can't play — check your internet connection",
                )
            }
        })
        controller = c
        scope.launch { while (isActive) { if (c.isPlaying) sync(); delay(500) } }
        return c
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = _state.value.copy(
            number = item?.mediaId?.toIntOrNull(),
            title = item?.mediaMetadata?.title?.toString() ?: "",
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasNext = c.hasNextMediaItem(), hasPrev = c.hasPreviousMediaItem(),
            error = if (c.playerError == null) null else _state.value.error,
        )
    }

    private fun item(h: HymnSummary) = MediaItem.Builder()
        .setMediaId(h.number.toString())
        .setUri(audio.uri(h.number))
        .setMediaMetadata(MediaMetadata.Builder().setTitle("${h.number}. ${h.title.titleCase()}").setArtist("FaithHymns").build())
        .build()

    /** Plays a single hymn, or a queue (playlist) starting at [start]. */
    fun play(queue: List<HymnSummary>, start: Int = 0) = scope.launch {
        val c = ctrl()
        _state.value = _state.value.copy(error = null)
        c.setMediaItems(queue.map(::item), start, 0)
        c.prepare(); c.play()
    }

    fun toggle() = scope.launch { val c = ctrl(); if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_ENDED) c.seekTo(0); c.play() } }
    fun seek(ms: Long) = scope.launch { ctrl().seekTo(ms); sync() }
    fun next() = scope.launch { ctrl().seekToNextMediaItem() }
    fun prev() = scope.launch { ctrl().seekToPreviousMediaItem() }
    fun stop() = scope.launch { ctrl().run { stop(); clearMediaItems() }; _state.value = PlayerState() }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}

fun String.titleCase(): String = lowercase().split(" ").joinToString(" ") { w ->
    w.replaceFirstChar { it.titlecase() }
}
