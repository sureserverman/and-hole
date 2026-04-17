package org.pihole.android.feature.lists

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.data.db.entity.AdlistSourceEntity
import java.text.DateFormat
import java.util.Date

/** Enabled sources older than this since [AdlistSourceEntity.lastSuccessAt] are marked stale. */
private const val STALE_AFTER_MS: Long = 7L * 24 * 60 * 60 * 1000

private enum class ListAttention {
    NeverFetched,
    RefreshFailed,
    Stale,
}

private fun attentionFor(entity: AdlistSourceEntity, nowMs: Long): ListAttention? {
    if (!entity.enabled) return null
    if (entity.lastError != null) return ListAttention.RefreshFailed
    val successAt = entity.lastSuccessAt
    if (successAt == null) return ListAttention.NeverFetched
    if (nowMs - successAt > STALE_AFTER_MS) return ListAttention.Stale
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
) {
    // Use collectAsState (not lifecycle-aware) so the list updates while the add dialog is open.
    // collectAsStateWithLifecycle pauses collection below STARTED; modal dialogs can leave the
    // scaffold body in a state where the Room-backed list would not refresh until the dialog closes.
    val sources by viewModel.sources.collectAsState()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val urlError by viewModel.urlError.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var draftUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.lists_screen_title),
                        modifier = Modifier.testTag("lists_top_bar_title"),
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.refreshNow() },
                        enabled = !refreshing,
                        modifier = Modifier.testTag("lists_refresh_button"),
                    ) {
                        Text(
                            if (refreshing) {
                                stringResource(R.string.lists_refreshing)
                            } else {
                                stringResource(R.string.lists_refresh_now)
                            },
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.clearUrlError()
                            draftUrl = ""
                            showAddDialog = true
                        },
                        modifier = Modifier.testTag("lists_add_button"),
                    ) {
                        Text(stringResource(R.string.lists_add))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (sources.isEmpty()) {
            Text(
                stringResource(R.string.lists_empty),
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .testTag("lists_empty_state"),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            val nowMs = System.currentTimeMillis()
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sources, key = { it.id }) { entity ->
                    AdlistSourceRow(
                        entity = entity,
                        nowMs = nowMs,
                        onEnabledChange = { viewModel.setEnabled(entity, it) },
                        onDelete = { viewModel.deleteSource(entity) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                viewModel.clearUrlError()
            },
            title = { Text(stringResource(R.string.lists_add_source)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = {
                            draftUrl = it
                            viewModel.clearUrlError()
                        },
                        label = { Text(stringResource(R.string.lists_url_hint)) },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("lists_url_input"),
                        isError = urlError != null,
                        supportingText =
                            urlError?.let { err ->
                                { Text(err) }
                            },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addSource(draftUrl) {
                            showAddDialog = false
                            draftUrl = ""
                        }
                    },
                    modifier = Modifier.testTag("lists_confirm_add"),
                ) {
                    Text(stringResource(R.string.lists_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        viewModel.clearUrlError()
                    },
                ) {
                    Text(stringResource(R.string.lists_cancel))
                }
            },
        )
    }
}

@Composable
private fun AdlistSourceRow(
    entity: AdlistSourceEntity,
    nowMs: Long,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val attention = attentionFor(entity, nowMs)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("lists_source_row_${entity.id}"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(entity.url, style = MaterialTheme.typography.titleSmall)
            attention?.let { a ->
                Text(
                    stringResource(R.string.lists_needs_update),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("lists_source_needs_update_${entity.id}"),
                )
                val detail =
                    when (a) {
                        ListAttention.NeverFetched -> stringResource(R.string.lists_needs_update_detail_never)
                        ListAttention.RefreshFailed -> stringResource(R.string.lists_needs_update_detail_error)
                        ListAttention.Stale -> stringResource(R.string.lists_needs_update_detail_stale)
                    }
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lists_source_needs_update_detail_${entity.id}"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.lists_enabled), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = entity.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("lists_source_enabled_${entity.id}"),
                )
            }
            entity.lastResult?.let { r ->
                Text(
                    "${stringResource(R.string.lists_last_result)}: $r",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lists_source_result_${entity.id}"),
                )
            }
            entity.lastSuccessAt?.let { t ->
                Text(
                    "${stringResource(R.string.lists_last_ok)}: ${df.format(Date(t))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            entity.lastError?.let { e ->
                Text(
                    "${stringResource(R.string.lists_last_error_label)}: $e",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lists_source_error_${entity.id}"),
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("lists_source_delete_${entity.id}"),
            ) {
                Text(stringResource(R.string.lists_delete))
            }
        }
    }
}
