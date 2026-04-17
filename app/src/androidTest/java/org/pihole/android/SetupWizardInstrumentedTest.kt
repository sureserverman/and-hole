package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.prefs.AppPreferences

@RunWith(AndroidJUnit4::class)
class SetupWizardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Test
    fun onboarding_appearsWhenIncomplete_andCanBeDismissed() {
        runBlocking {
            val prefs = AppPreferences(composeRule.activity.applicationContext)
            prefs.setOnboardingCompleted(false)
            prefs.setSetupClientMode("")
            prefs.setSetupBindAllInterfacesDismissed(false)
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("setup_wizard_card").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("setup_wizard_card").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_mode_sing_box").performClick()
        composeRule.onNodeWithTag("setup_complete_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("setup_wizard_card").fetchSemanticsNodes().isEmpty()
            }.getOrDefault(false)
        }
    }
}
