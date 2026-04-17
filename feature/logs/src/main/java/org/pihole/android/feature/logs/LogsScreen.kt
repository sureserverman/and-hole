package org.pihole.android.feature.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.data.db.entity.QueryLogEntity
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    vm: LogsViewModel,
) {
    val rows by vm.visibleLogs.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val searchText by vm.searchQuery.collectAsStateWithLifecycle()
    val decisionFilter by vm.decisionFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is LogsUiEvent.ShareExport -> {
                    val intent = LogsExportWriter.buildShareIntent(ev.export)
                    context.startActivity(Intent.createChooser(intent, "Share logs"))
                }
                is LogsUiEvent.ToastMessage -> {
                    android.widget.Toast.makeText(context, ev.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Logs", modifier = Modifier.testTag("logs_top_bar_title")) },
                actions = {
                    TextButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("logs_export"),
                    ) {
                        Text("Export")
                    }
                    TextButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.testTag("logs_clear"),
                    ) {
                        Text("Clear")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LogsInsightsCard(state = insights)
            }
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { vm.setSearchQuery(it) },
                    label = { Text(stringResource(R.string.logs_search_hint)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("logs_search_field"),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = decisionFilter == LogDecisionFilter.ALL,
                        onClick = { vm.setDecisionFilter(LogDecisionFilter.ALL) },
                        label = { Text(stringResource(R.string.logs_filter_all)) },
                        modifier = Modifier.testTag("logs_filter_all"),
                    )
                    FilterChip(
                        selected = decisionFilter == LogDecisionFilter.BLOCKED,
                        onClick = { vm.setDecisionFilter(LogDecisionFilter.BLOCKED) },
                        label = { Text(stringResource(R.string.logs_filter_blocked)) },
                        modifier = Modifier.testTag("logs_filter_blocked"),
                    )
                    FilterChip(
                        selected = decisionFilter == LogDecisionFilter.ALLOWED,
                        onClick = { vm.setDecisionFilter(LogDecisionFilter.ALLOWED) },
                        label = { Text(stringResource(R.string.logs_filter_allowed)) },
                        modifier = Modifier.testTag("logs_filter_allowed"),
                    )
                    FilterChip(
                        selected = decisionFilter == LogDecisionFilter.PASS,
                        onClick = { vm.setDecisionFilter(LogDecisionFilter.PASS) },
                        label = { Text(stringResource(R.string.logs_filter_pass)) },
                        modifier = Modifier.testTag("logs_filter_pass"),
                    )
                }
            }
            items(rows, key = { it.id }) { row ->
                LogRow(
                    row = row,
                    onAllow = { vm.addExactAllowFromBlocked(it) },
                    onBlock = { vm.addExactDenyFromAllowed(it) },
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export logs") },
            text = { Text("Choose a format to share.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        vm.export(LogsExportFormat.Csv)
                    },
                    modifier = Modifier.testTag("logs_export_csv"),
                ) {
                    Text("CSV")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        vm.export(LogsExportFormat.Jsonl)
                    },
                    modifier = Modifier.testTag("logs_export_jsonl"),
                ) {
                    Text("JSONL")
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear logs?") },
            text = { Text("This will delete all saved query log entries on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        vm.clearLogs()
                    },
                    modifier = Modifier.testTag("logs_clear_confirm"),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.testTag("logs_clear_cancel"),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun LogRow(
    row: QueryLogEntity,
    onAllow: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.qname,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatTimestamp(row.timestamp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "qtype=${row.qtype} rcode=${row.responseCode} latency=${row.latencyMs}ms cache=${row.answeredFromCache}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = formatAttribution(row),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        RowActions(
            decision = row.decision,
            qname = row.qname,
            onAllow = onAllow,
            onBlock = onBlock,
        )
    }
}

@Composable
private fun RowActions(
    decision: String,
    qname: String,
    onAllow: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    when (decision) {
        "blocked" -> Button(onClick = { onAllow(qname) }, modifier = Modifier.testTag("log_row_allow_${qname}")) { Text("Allow") }
        "allowed" -> Button(onClick = { onBlock(qname) }, modifier = Modifier.testTag("log_row_block_${qname}")) { Text("Block") }
        else -> {}
    }
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMs: Long): String =
    runCatching { timestampFormatter.format(Instant.ofEpochMilli(epochMs)) }.getOrDefault("—")

private fun formatAttribution(row: QueryLogEntity): String =
    when {
        row.decision == "blocked" && row.matchedSourceId != null ->
            if (row.matchedSourceId == MATCHED_SUBSCRIBED_LIST_SENTINEL) {
                "Match: subscribed list"
            } else {
                "Match: subscribed list (source id ${row.matchedSourceId})"
            }
        row.decision == "blocked" && row.matchedRuleId != null ->
            if (row.matchedRuleId == MATCHED_CUSTOM_RULE_SENTINEL) {
                "Match: custom rule"
            } else {
                "Match: custom rule id ${row.matchedRuleId}"
            }
        row.decision == "blocked" ->
            "Match: blocklist / filter"
        row.decision == "allowed" && row.answeredFromCache ->
            "Path: response cache"
        row.decision == "allowed" ->
            "Path: allowed (exact allow or local/static answer)"
        row.decision == "pass" ->
            "Path: not blocked locally; forwarded upstream (Tor+DoT)"
        else -> "decision=${row.decision}"
    }

private const val MATCHED_SUBSCRIBED_LIST_SENTINEL: Long = -1L
private const val MATCHED_CUSTOM_RULE_SENTINEL: Long = -2L
