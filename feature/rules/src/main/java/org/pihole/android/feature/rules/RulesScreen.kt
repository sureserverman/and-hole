package org.pihole.android.feature.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.core.designsystem.components.AhAppBar
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.components.Pill
import org.pihole.android.core.designsystem.components.PillSwitch
import org.pihole.android.core.designsystem.components.PillVariant
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.core.designsystem.theme.AhTheme
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity

private enum class RulesTab { Exact, Regex, Local }

@Composable
fun RulesScreen(vm: RulesViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val localRecords by vm.localRecords.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(RulesTab.Exact) }

    var showAddDialog by remember { mutableStateOf(false) }
    var draftValue by remember { mutableStateOf("") }
    var draftIsAllow by remember { mutableStateOf(false) }
    var draftIsRegex by remember { mutableStateOf(false) }

    var showLocalDialog by remember { mutableStateOf(false) }
    var draftLocalName by remember { mutableStateOf("") }
    var draftLocalValue by remember { mutableStateOf("") }
    var draftLocalTtl by remember { mutableStateOf("300") }
    var draftLocalQtype by remember { mutableIntStateOf(RulesViewModel.QTYPE_CHOICES.first().first) }

    val exactRules = rules.filter { it.kind.startsWith("exact_") }
    val regexRules = rules.filter { it.kind.startsWith("regex_") }

    Scaffold(
        containerColor = AhTheme.colors.bg,
        topBar = {
            AhAppBar(
                title = stringResource(R.string.rules_screen_title),
                sub = "${exactRules.size} exact · ${regexRules.size} regex",
                modifier = Modifier.testTag("rules_top_bar_title"),
                right = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, AhTheme.colors.accent, CircleShape)
                            .clickable {
                                draftValue = ""
                                draftIsAllow = false
                                draftIsRegex = activeTab == RulesTab.Regex
                                showAddDialog = true
                            }
                            .testTag("rules_add_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AhIcons.Plus,
                            contentDescription = stringResource(R.string.rules_add),
                            tint = AhTheme.colors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            TabRow(active = activeTab, onSelect = { activeTab = it })
            ActionChips(
                onAllow = {
                    draftValue = ""
                    draftIsAllow = true
                    draftIsRegex = activeTab == RulesTab.Regex
                    showAddDialog = true
                },
                onDeny = {
                    draftValue = ""
                    draftIsAllow = false
                    draftIsRegex = activeTab == RulesTab.Regex
                    showAddDialog = true
                },
                onAddLocal = {
                    draftLocalName = ""
                    draftLocalValue = ""
                    draftLocalTtl = "300"
                    draftLocalQtype = RulesViewModel.QTYPE_CHOICES.first().first
                    showLocalDialog = true
                },
                tab = activeTab,
            )

            when (activeTab) {
                RulesTab.Exact -> RuleList(
                    rules = exactRules,
                    fallbackEmpty = stringResource(R.string.rules_no_custom_rules),
                    onEnabled = vm::setRuleEnabled,
                    onDelete = vm::deleteRule,
                )
                RulesTab.Regex -> RuleList(
                    rules = regexRules,
                    fallbackEmpty = stringResource(R.string.rules_no_custom_rules),
                    onEnabled = vm::setRuleEnabled,
                    onDelete = vm::deleteRule,
                )
                RulesTab.Local -> LocalList(
                    records = localRecords,
                    onEnabled = vm::setLocalRecordEnabled,
                    onDelete = vm::deleteLocalRecord,
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.rules_add_rule_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !draftIsRegex,
                            onClick = { draftIsRegex = false },
                            label = { Text(stringResource(R.string.rules_exact)) },
                            modifier = Modifier.testTag("rules_add_mode_exact"),
                        )
                        FilterChip(
                            selected = draftIsRegex,
                            onClick = { draftIsRegex = true },
                            label = { Text(stringResource(R.string.rules_regex)) },
                            modifier = Modifier.testTag("rules_add_mode_regex"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = draftIsAllow,
                            onClick = { draftIsAllow = true },
                            label = { Text(stringResource(R.string.rules_allow)) },
                            modifier = Modifier.testTag("rules_add_kind_allow"),
                        )
                        FilterChip(
                            selected = !draftIsAllow,
                            onClick = { draftIsAllow = false },
                            label = { Text(stringResource(R.string.rules_block)) },
                            modifier = Modifier.testTag("rules_add_kind_block"),
                        )
                    }
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        label = { Text(stringResource(R.string.rules_domain_hint)) },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rules_add_domain_field"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        if (draftIsRegex) {
                            vm.addRegexRule(isAllow = draftIsAllow, pattern = draftValue)
                        } else {
                            vm.addExactRule(isAllow = draftIsAllow, rawDomain = draftValue)
                        }
                    },
                    enabled = draftValue.trim().isNotEmpty(),
                    modifier = Modifier.testTag("rules_add_confirm"),
                ) {
                    Text(stringResource(R.string.rules_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    modifier = Modifier.testTag("rules_add_cancel"),
                ) {
                    Text(stringResource(R.string.rules_cancel))
                }
            },
        )
    }

    if (showLocalDialog) {
        AlertDialog(
            onDismissRequest = { showLocalDialog = false },
            title = { Text(stringResource(R.string.rules_add_local_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftLocalName,
                        onValueChange = { draftLocalName = it },
                        label = { Text(stringResource(R.string.rules_local_name_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rules_local_name_field"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((code, label) in RulesViewModel.QTYPE_CHOICES) {
                            FilterChip(
                                selected = draftLocalQtype == code,
                                onClick = { draftLocalQtype = code },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rules_local_type_$code"),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draftLocalValue,
                        onValueChange = { draftLocalValue = it },
                        label = { Text(stringResource(R.string.rules_local_value_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rules_local_value_field"),
                    )
                    OutlinedTextField(
                        value = draftLocalTtl,
                        onValueChange = { draftLocalTtl = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.rules_local_ttl_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rules_local_ttl_field"),
                    )
                }
            },
            confirmButton = {
                val ttlOk = draftLocalTtl.toIntOrNull()?.let { it in 30..86_400 } == true
                TextButton(
                    onClick = {
                        showLocalDialog = false
                        vm.addLocalRecord(
                            name = draftLocalName,
                            qtype = draftLocalQtype,
                            value = draftLocalValue,
                            ttl = draftLocalTtl.toIntOrNull() ?: 300,
                        )
                    },
                    enabled = draftLocalName.trim().isNotEmpty() && draftLocalValue.trim().isNotEmpty() && ttlOk,
                    modifier = Modifier.testTag("rules_local_confirm"),
                ) {
                    Text(stringResource(R.string.rules_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalDialog = false }) {
                    Text(stringResource(R.string.rules_cancel))
                }
            },
        )
    }
}

@Composable
private fun TabRow(active: RulesTab, onSelect: (RulesTab) -> Unit) {
    val c = AhTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TabItem(label = "Exact", selected = active == RulesTab.Exact) { onSelect(RulesTab.Exact) }
        TabItem(label = "Regex", selected = active == RulesTab.Regex) { onSelect(RulesTab.Regex) }
        TabItem(label = "Local DNS", selected = active == RulesTab.Local) { onSelect(RulesTab.Local) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.border),
    )
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = AhTheme.colors
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) c.accent else c.textMute,
            style = AhTheme.text.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(width = 40.dp, height = 2.dp)
                .background(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun ActionChips(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onAddLocal: () -> Unit,
    tab: RulesTab,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tab == RulesTab.Local) {
            Pill(
                text = "+ Local DNS",
                variant = PillVariant.Ghost,
                onClick = onAddLocal,
                modifier = Modifier.testTag("rules_add_local_button"),
            )
        } else {
            Pill(text = "+ Allow", variant = PillVariant.Ghost, onClick = onAllow)
            Pill(text = "+ Deny", variant = PillVariant.Danger, onClick = onDeny)
        }
    }
}

@Composable
private fun RuleList(
    rules: List<CustomRuleEntity>,
    fallbackEmpty: String,
    onEnabled: (CustomRuleEntity, Boolean) -> Unit,
    onDelete: (CustomRuleEntity) -> Unit,
) {
    if (rules.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp).testTag("rules_empty_state")) {
            Text(text = fallbackEmpty, style = AhTheme.text.body, color = AhTheme.colors.textMute)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rules, key = { it.id }) { rule ->
            RuleRow(rule = rule, onEnabledChange = { onEnabled(rule, it) }, onDelete = { onDelete(rule) })
        }
    }
}

@Composable
private fun LocalList(
    records: List<LocalDnsRecordEntity>,
    onEnabled: (LocalDnsRecordEntity, Boolean) -> Unit,
    onDelete: (LocalDnsRecordEntity) -> Unit,
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.rules_local_empty), style = AhTheme.text.body, color = AhTheme.colors.textMute)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(records, key = { it.id }) { rec ->
            LocalRow(record = rec, onEnabledChange = { onEnabled(rec, it) }, onDelete = { onDelete(rec) })
        }
    }
}

@Composable
private fun RuleRow(
    rule: CustomRuleEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val c = AhTheme.colors
    val isAllow = rule.kind.endsWith("_allow")
    val avatarColor = if (isAllow) c.accent else c.blocked
    val avatarGlyph = if (isAllow) "✓" else "×"

    AhCard(
        modifier = Modifier.fillMaxWidth().testTag("rules_rule_row_${rule.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.18f))
                    .border(1.dp, avatarColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = avatarGlyph, color = avatarColor, style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.value,
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    maxLines = 1,
                )
                Text(
                    text = "${if (isAllow) "Allow" else "Deny"} · ${rule.kind.removeSuffix("_allow").removeSuffix("_deny")}${rule.comment?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = AhTheme.text.monoCaption,
                    color = c.textMute,
                    maxLines = 1,
                )
            }
            PillSwitch(
                checked = rule.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("rules_rule_enabled_${rule.id}"),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("rules_rule_delete_${rule.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AhIcons.Trash,
                    contentDescription = stringResource(R.string.rules_delete),
                    tint = c.textMute,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalRow(
    record: LocalDnsRecordEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val c = AhTheme.colors
    AhCard(
        modifier = Modifier.fillMaxWidth().testTag("rules_local_row_${record.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = record.type.toString(), style = AhTheme.text.monoCaption, color = c.textMute)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    maxLines = 1,
                )
                Text(
                    text = "${record.value} · ttl=${record.ttl}",
                    style = AhTheme.text.monoCaption,
                    color = c.textMute,
                    maxLines = 1,
                )
            }
            PillSwitch(
                checked = record.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("rules_local_enabled_${record.id}"),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("rules_local_delete_${record.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AhIcons.Trash,
                    contentDescription = stringResource(R.string.rules_delete),
                    tint = c.textMute,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
