package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.pihole.android.feature.lists.R as ListsR
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.db.DatabaseProvider

@RunWith(AndroidJUnit4::class)
class ListsScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    @Before
    fun wakeAndClearLists() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.executeShellCommand("svc power stayon true")
        UiDevice.getInstance(inst).wakeUp()
        val context = inst.targetContext
        runBlocking {
            DatabaseProvider.get(context).clearAllTables()
        }
    }

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun navigateToLists() {
        waitForHome()
        composeRule.onNodeWithTag("bottom_nav_lists").performClick()
        composeRule.waitUntil(timeoutMillis = 25_000) {
            runCatching {
                composeRule.onAllNodesWithTag("lists_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun listsScreen_showsRefreshAndAddActions() {
        navigateToLists()
        composeRule.onNodeWithTag("lists_refresh_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lists_add_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lists_empty_state").assertIsDisplayed()
    }

    @Test
    fun listsScreen_addSource_showsRowWithUrl() {
        navigateToLists()
        val url = "https://example.com/adlist.txt"
        composeRule.onNodeWithTag("lists_add_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lists_url_input", useUnmergedTree = true).performTextReplacement(url)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lists_confirm_add").performClick()
        composeRule.waitForIdle()
        // Wait until the add dialog is gone; otherwise onNodeWithText(url) matches the TextField, not the row.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("lists_url_input", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }.getOrDefault(false)
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onNodeWithText(url, substring = true, ignoreCase = false).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText(url, substring = true).assertIsDisplayed()
        val deleteLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(ListsR.string.lists_delete)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onNodeWithText(deleteLabel).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText(deleteLabel).assertIsDisplayed()
        val rowId =
            runBlocking {
                DatabaseProvider.get(InstrumentationRegistry.getInstrumentation().targetContext)
                    .adlistSourceDao()
                    .getAll()
                    .single { it.url == url }
                    .id
            }
        composeRule.onNodeWithTag("lists_source_enabled_$rowId").assertIsDisplayed()
    }
}
