package com.g41us.faithhymn

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class TopicsShot {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun shot(n: String) { Thread.sleep(600); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle(); rule.onRoot().captureRoboImage("screenshots/$n.png") }
    @Test fun topics() {
        rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        rule.onAllNodesWithText("Skip").onFirst().performClick(); shot("t0_home")
        rule.onAllNodesWithText("Prayer").onFirst().performClick(); shot("t1_topic")
    }
}
