package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Before
    fun wakeScreen() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.executeShellCommand("svc power stayon true")
        UiDevice.getInstance(inst).wakeUp()
    }

    @Test
    fun homeScreen_titleVisible() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("home_top_bar_title").assertIsDisplayed()
    }
}
