package com.g41us.faithhymn

import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.g41us.faithhymn.audio.PlayerState
import com.g41us.faithhymn.data.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders every screen with a fake 24dp status bar + 24dp gesture bar (Galaxy S21 size). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as FaithHymnApp

    private fun insets() {
        rule.activityRule.scenario.onActivity { a ->
            val root: View = a.window.decorView
            val d = a.resources.displayMetrics.density
            ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * d).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * d).toInt())).build())
        }
        rule.waitForIdle()
    }
    private fun shot(name: String) { insets(); Thread.sleep(600); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle(); Thread.sleep(200); rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/$name.png") }
    private fun tap(text: String) = rule.onAllNodesWithText(text).onFirst().performClick()

    @Test fun screens() {
        shot("01_onboarding")
        tap("Skip"); shot("02_home")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(12); shot("03_home_scrolled")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(4)

        tap("All Hail, Immanuel!"); shot("04_reader_hymn1")
        rule.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("HYMN 1"))).onLast().performTouchInput { swipeUp() }; shot("05_reader_scrolled")
        tap("S"); shot("06_reader_soprano")
        rule.activity.onBackPressedDispatcher.let { rule.runOnUiThread { it.onBackPressed() } }

        tap("Search"); rule.onNode(hasSetTextAction()).performTextInput("amazng grase"); shot("07_search_typo")
        rule.onNode(hasSetTextAction()).performTextClearance(); rule.onNode(hasSetTextAction()).performTextInput("blessed"); shot("08_search_grouped")
        rule.onNode(hasSetTextAction()).performTextClearance(); shot("09_search_empty")

        runBlocking {
            app.repo.toggleFavourite(445); app.repo.toggleFavourite(457); app.repo.toggleFavourite(37)
            val id = app.repo.createPlaylist("Sunday Service", "2026-11-29", listOf(1, 445, 457, 37, 712))
            app.repo.setSlot(id, 1, "Opening"); app.repo.setSlot(id, 457, "Offertory"); app.repo.setSlot(id, 712, "Closing")
            app.repo.createPlaylist("Choir practice", null, listOf(138, 448))
        }
        tap("Library"); shot("10_library_favs")
        tap("Playlists"); shot("11_library_playlists")
        tap("Sunday Service"); shot("12_playlist")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        tap("Settings"); shot("13_settings")
        rule.onNodeWithText("Show music markings").performScrollTo(); shot("14_settings_mid")
        rule.onNodeWithText("Share FaithHymns").performScrollTo(); shot("15_settings_bottom")

        app.player.previewState(PlayerState(number = 457, title = "457. Amazing Grace", playing = true, positionMs = 62_000, durationMs = 180_000, queueSize = 5, queueIndex = 2, hasNext = true))
        tap("Hymns"); shot("16_home_miniplayer")
        rule.onNodeWithText("Amazing Grace", substring = true).performClick(); shot("17_player")
        tap("Lyrics"); shot("18_player_lyrics")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        app.settings.update { it.copy(theme = ThemeMode.NIGHT) }
        tap("Hymns"); shot("19_night_home")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(4)
        tap("All Hail, Immanuel!"); shot("20_night_reader")
        app.settings.update { it.copy(theme = ThemeMode.LIGHT, presentLeader = true) }
        rule.onNodeWithContentDescription("More").performClick(); tap("Present on screen"); shot("21_present")
    }
}
