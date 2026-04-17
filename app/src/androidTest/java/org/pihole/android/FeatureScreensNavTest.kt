package org.pihole.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureScreensNavTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Before
    fun wakeScreen() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.executeShellCommand("svc power stayon true")
        UiDevice.getInstance(inst).wakeUp()
    }

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun clickBottomNav(route: String) {
        composeRule.onNodeWithTag("bottom_nav_$route").performClick()
    }

    private fun waitForTopBar(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun bottomNav_eachDestination_showsTopBarAndPrimaryContent() {
        waitForHome()
        assertTrue(runCatching { composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("home_control_status_card").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("bottom_nav_home").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("bottom_nav_rules").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("bottom_nav_logs").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("bottom_nav_lists").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("bottom_nav_settings").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))

        clickBottomNav("rules")
        waitForTopBar("rules_top_bar_title")
        assertTrue(runCatching { composeRule.onAllNodesWithTag("rules_top_bar_title").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_empty_state").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        assertTrue(runCatching { composeRule.onAllNodesWithTag("rules_empty_state").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))

        clickBottomNav("logs")
        waitForTopBar("logs_top_bar_title")
        assertTrue(runCatching { composeRule.onAllNodesWithTag("logs_top_bar_title").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("logs_export").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))

        clickBottomNav("lists")
        waitForTopBar("lists_top_bar_title")
        assertTrue(runCatching { composeRule.onAllNodesWithTag("lists_top_bar_title").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(
            runCatching { composeRule.onAllNodesWithTag("lists_refresh_button").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false),
        )

        clickBottomNav("settings")
        waitForTopBar("settings_top_bar_title")
        assertTrue(runCatching { composeRule.onAllNodesWithTag("settings_top_bar_title").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(runCatching { composeRule.onAllNodesWithTag("settings_retention_days_plus").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false))
        assertTrue(
            runCatching { composeRule.onAllNodesWithTag("settings_open_diagnostics").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false),
        )
    }
}
