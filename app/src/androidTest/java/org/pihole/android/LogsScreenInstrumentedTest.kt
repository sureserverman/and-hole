package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun logsScreen_rendersInsightsSection() {
        composeRule.onNodeWithTag("bottom_nav_logs").performClick()
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("logs_top_bar_title").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("logs_insights_card").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_insights_decisions").assertIsDisplayed()
        composeRule.onNodeWithTag("logs_insights_top_blocked").assertIsDisplayed()
    }
}
