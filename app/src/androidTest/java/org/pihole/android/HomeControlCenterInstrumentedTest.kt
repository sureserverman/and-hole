package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeControlCenterInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun home_showsControlCenterCardsAndActions() {
        composeRule.onNodeWithTag("home_control_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_start").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("home_action_refresh_lists").assertIsDisplayed()
        composeRule.onNodeWithTag("home_runtime_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_counts_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_recent_blocked_card").assertIsDisplayed()
    }
}
