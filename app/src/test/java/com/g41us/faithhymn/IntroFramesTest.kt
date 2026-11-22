package com.g41us.faithhymn

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
class IntroFramesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun frames() {
        rule.mainClock.autoAdvance = false
        for (i in 0 until 34) { rule.onRoot().captureRoboImage("intro/f%02d.png".format(i)); rule.mainClock.advanceTimeBy(60) }
    }
}
