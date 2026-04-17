package org.pihole.android.feature.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    vm: RulesViewModel,
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val localRecords by vm.localRecords.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var draftValue by remember { mutableStateOf("") }
    var draftIsAllow by remember { mutableStateOf(false) }
    var draftIsRegex by remember { mutableStateOf(false) }

    var showLocalDialog by remember { mutableStateOf(false) }
    var draftLocalName by remember { mutableStateOf("") }
    var draftLocalValue by remember { mutableStateOf("") }
    var draftLocalTtl by remember { mutableStateOf("300") }
    var draftLocalQtype by remember { mutableIntStateOf(RulesViewModel.QTYPE_CHOICES.first().first) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.rules_screen_title),
                        modifier = Modifier.testTag("rules_top_bar_title"),
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            draftValue = ""
                            draftIsAllow = false
                            draftIsRegex = false
                            showAddDialog = true
                        },
                        modifier = Modifier.testTag("rules_add_button"),
                    ) {
                        Text(stringResource(R.string.rules_add))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.rules_section_custom), style = MaterialTheme.typography.titleMedium)
            }
            if (rules.isEmpty()) {
                item {
                    Text(
                        text =
                            if (localRecords.isEmpty()) {
                                stringResource(R.string.rules_empty)
                            } else {
                                stringResource(R.string.rules_no_custom_rules)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("rules_empty_state"),
                    )
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onEnabledChange = { vm.setRuleEnabled(rule, it) },
                        onDelete = { vm.deleteRule(rule) },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(stringResource(R.string.rules_section_local), style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text(
                    stringResource(R.string.rules_local_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (localRecords.isEmpty()) {
                item {
                    Text(stringResource(R.string.rules_local_empty), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(localRecords, key = { it.id }) { rec ->
                    LocalDnsRecordRow(
                        record = rec,
                        onEnabledChange = { vm.setLocalRecordEnabled(rec, it) },
                        onDelete = { vm.deleteLocalRecord(rec) },
                    )
                }
            }
            item {
                TextButton(
                    onClick = {
                        draftLocalName = ""
                        draftLocalValue = ""
                        draftLocalTtl = "300"
                        draftLocalQtype = RulesViewModel.QTYPE_CHOICES.first().first
                        showLocalDialog = true
                    },
                    modifier = Modifier.testTag("rules_add_local_button"),
                ) {
                    Text(stringResource(R.string.rules_add_local_record))
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.rules_add_rule_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                        modifier =
                            Modifier
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
                        modifier =
                            Modifier
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("rules_local_value_field"),
                    )
                    OutlinedTextField(
                        value = draftLocalTtl,
                        onValueChange = { draftLocalTtl = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.rules_local_ttl_hint)) },
                        singleLine = true,
                        modifier =
                            Modifier
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
                    enabled =
                        draftLocalName.trim().isNotEmpty() &&
                            draftLocalValue.trim().isNotEmpty() &&
                            ttlOk,
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
private fun RuleRow(
    rule: CustomRuleEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("rules_rule_row_${rule.id}"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(rule.kind, style = MaterialTheme.typography.labelMedium)
            Text(rule.value, style = MaterialTheme.typography.bodyLarge)
            rule.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rules_enabled), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("rules_rule_enabled_${rule.id}"),
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("rules_rule_delete_${rule.id}"),
            ) {
                Text(stringResource(R.string.rules_delete))
            }
        }
    }
}

@Composable
private fun LocalDnsRecordRow(
    record: LocalDnsRecordEntity,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("rules_local_row_${record.id}"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "type=${record.type} ttl=${record.ttl}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(record.name, style = MaterialTheme.typography.bodyLarge)
            Text(record.value, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rules_enabled), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = record.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("rules_local_enabled_${record.id}"),
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("rules_local_delete_${record.id}"),
            ) {
                Text(stringResource(R.string.rules_delete))
            }
        }
    }
}
