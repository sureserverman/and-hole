package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Before
    fun wakeScreen() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.executeShellCommand("svc power stayon true")
        UiDevice.getInstance(inst).wakeUp()
        // Some OEM ROMs keep the activity backgrounded behind keyguard unless we dismiss it explicitly.
        runCatching { inst.uiAutomation.executeShellCommand("wm dismiss-keyguard") }
    }

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun diagnosticsScreen_showsSectionsAndCopyAction() {
        waitForHome()
        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("settings_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("settings_open_diagnostics").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("diagnostics_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("diagnostics_top_bar_title").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_scroll").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_section_build").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_copy_report").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_section_prefs").assertIsDisplayed()
    }
}
