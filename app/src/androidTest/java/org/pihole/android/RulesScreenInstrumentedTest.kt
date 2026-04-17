package org.pihole.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.db.entity.CustomRuleEntity

@RunWith(AndroidJUnit4::class)
class RulesScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeHostActivity>()

    private val appContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun wakeScreen() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.executeShellCommand("svc power stayon true")
        UiDevice.getInstance(inst).wakeUp()
    }

    @After
    fun clearRules() = runBlocking {
        val db = DatabaseProvider.get(appContext)
        db.customRuleDao().getAll().forEach { db.customRuleDao().deleteById(it.id) }
        db.localDnsRecordDao().getAll().forEach { db.localDnsRecordDao().deleteById(it.id) }
    }

    @Test
    fun rulesScreen_showsSeededRuleRow() {
        val now = System.currentTimeMillis()
        val id =
            runBlocking {
                DatabaseProvider.get(appContext).customRuleDao().insert(
                    CustomRuleEntity(
                        kind = "exact_deny",
                        value = "instrumented.rules.test.",
                        enabled = true,
                        comment = "instrumented test",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("bottom_nav_rules").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_rule_row_$id").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("rules_rule_row_$id").assertIsDisplayed()
        composeRule.onNodeWithTag("rules_rule_delete_$id").assertIsDisplayed()

        composeRule.onNodeWithTag("rules_rule_delete_$id").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_empty_state").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("rules_empty_state").assertIsDisplayed()
        assertTrue(
            runBlocking { DatabaseProvider.get(appContext).customRuleDao().getById(id) } == null,
        )
    }

    @Test
    fun rulesScreen_addViaDialog_showsRow() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("bottom_nav_rules").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_empty_state").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("rules_add_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_add_domain_field").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("rules_add_domain_field").performTextInput("manual.rules.test.")
        composeRule.onNodeWithTag("rules_add_kind_block").performClick()
        composeRule.onNodeWithTag("rules_add_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                DatabaseProvider.get(appContext).customRuleDao().getAll().any {
                    it.value == "manual.rules.test." && it.kind == "exact_deny"
                }
            }
        }
        val row =
            runBlocking {
                DatabaseProvider.get(appContext).customRuleDao().getAll()
                    .first { it.value == "manual.rules.test." }
            }
        composeRule.onNodeWithTag("rules_rule_row_${row.id}").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_toggleSwitch_updatesEnabledInDb() {
        val now = System.currentTimeMillis()
        val id =
            runBlocking {
                DatabaseProvider.get(appContext).customRuleDao().insert(
                    CustomRuleEntity(
                        kind = "exact_deny",
                        value = "toggle.rules.test.",
                        enabled = true,
                        comment = "toggle test",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onAllNodesWithTag("home_top_bar_title").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("bottom_nav_rules").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithTag("rules_rule_enabled_$id").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("rules_rule_enabled_$id").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                DatabaseProvider.get(appContext).customRuleDao().getById(id)?.enabled == false
            }
        }
        assertFalse(
            runBlocking { DatabaseProvider.get(appContext).customRuleDao().getById(id)!!.enabled },
        )
    }
}
