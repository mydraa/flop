package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.CourseEvent
import com.example.ui.components.CourseCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun course_card_screenshot() {
        val course = CourseEvent(
            id = "demo_test",
            title = "Algorithmique Avancée & Graphes",
            room = "Amphi B104",
            teacher = "Pr. Turing",
            startTime = LocalDateTime.of(2026, 9, 14, 10, 0),
            endTime = LocalDateTime.of(2026, 9, 14, 12, 0)
        )
        val now = LocalDateTime.of(2026, 9, 14, 10, 45)

        composeTestRule.setContent {
            MyApplicationTheme {
                CourseCard(course = course, now = now)
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
