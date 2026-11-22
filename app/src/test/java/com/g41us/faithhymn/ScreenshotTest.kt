package com.g41us.faithhymn

import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders key screens with a fake 24dp status bar + 24dp gesture bar (Galaxy S21-ish, 1080x2400 @ 420dpi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun insets() {
        rule.activityRule.scenario.onActivity { a ->
            val root: View = a.window.decorView
            val d = a.resources.displayMetrics.density
            val ins = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (24 * d).toInt(), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (24 * d).toInt()))
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, ins)
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/$name.png") }

    @Test fun screens() {
        shot("1_hymns")
        rule.onAllNodesWithText("All Hail, Immanuel!").onFirst().performClick(); shot("2_hymn")
        rule.onNodeWithContentDescription("Favourite").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Search").performClick(); shot("3_search_empty")

        rule.onAllNodesWithText("Library").onFirst().performClick(); shot("4_library")
        rule.onAllNodesWithText("Settings").onFirst().performClick(); shot("5_settings")
        rule.onNodeWithText("Hymns in the book").performScrollTo(); shot("5b_settings_updates")
        rule.onAllNodesWithText("Hymns").onFirst().performClick(); shot("6_hymns_recent")
    }
}
