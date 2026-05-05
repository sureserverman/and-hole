package org.pihole.android.feature.lists

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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.pihole.android.data.db.entity.AdlistSourceEntity
import java.text.DateFormat
import java.util.Date

private const val STALE_AFTER_MS: Long = 7L * 24 * 60 * 60 * 1000

private enum class ListAttention { NeverFetched, RefreshFailed, Stale }

private fun attentionFor(entity: AdlistSourceEntity, nowMs: Long): ListAttention? {
    if (!entity.enabled) return null
    if (entity.lastError != null) return ListAttention.RefreshFailed
    val successAt = entity.lastSuccessAt
    if (successAt == null) return ListAttention.NeverFetched
    if (nowMs - successAt > STALE_AFTER_MS) return ListAttention.Stale
    return null
}

@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
) {
    val sources by viewModel.sources.collectAsState()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val urlError by viewModel.urlError.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var draftUrl by remember { mutableStateOf("") }

    Scaffold(
        containerColor = AhTheme.colors.bg,
        topBar = {
            AhAppBar(
                title = stringResource(R.string.lists_screen_title),
                sub = "${sources.size} sources",
                modifier = Modifier.testTag("lists_top_bar_title"),
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconAction(
                            icon = AhIcons.Refresh,
                            contentDescription = stringResource(R.string.lists_refresh_now),
                            testTag = "lists_refresh_button",
                            enabled = !refreshing,
                            onClick = { viewModel.refreshNow() },
                        )
                        IconAction(
                            icon = AhIcons.Plus,
                            contentDescription = stringResource(R.string.lists_add),
                            testTag = "lists_add_button",
                            onClick = {
                                viewModel.clearUrlError()
                                draftUrl = ""
                                showAddDialog = true
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (sources.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                AhCard(modifier = Modifier.fillMaxWidth().testTag("lists_empty_state")) {
                    Text(
                        text = stringResource(R.string.lists_empty),
                        style = AhTheme.text.body,
                        color = AhTheme.colors.textMute,
                    )
                }
            }
        } else {
            val nowMs = System.currentTimeMillis()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    CompositionCard(sources = sources)
                }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lists_url_input"),
                        isError = urlError != null,
                        supportingText = urlError?.let { err -> { Text(err) } },
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
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AhTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, c.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) c.text else c.textDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CompositionCard(sources: List<AdlistSourceEntity>) {
    val c = AhTheme.colors
    val palette = listOf(c.accent, c.warn, c.danger, c.allowed, c.pass, c.blocked)
    val active = sources.filter { it.enabled }.takeIf { it.isNotEmpty() } ?: sources
    val total = active.size.coerceAtLeast(1)

    AhCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Composition",
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
                Text(
                    text = "${active.size} active",
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                active.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(palette[i % palette.size]),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("ads", "trackers", "malware").forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette[i % palette.size]),
                        )
                        Text(text = label, style = AhTheme.text.monoCaption, color = c.textMute)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdlistSourceRow(
    entity: AdlistSourceEntity,
    nowMs: Long,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val c = AhTheme.colors
    val attention = attentionFor(entity, nowMs)
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val (statusText, statusVariant, statusColor) = when {
        !entity.enabled -> Triple("Off", PillVariant.Mute, c.textMute)
        attention == ListAttention.RefreshFailed -> Triple("Failed", PillVariant.Danger, c.danger)
        attention == ListAttention.Stale -> Triple("Stale", PillVariant.Warn, c.warn)
        attention == ListAttention.NeverFetched -> Triple("New", PillVariant.Mute, c.textMute)
        else -> Triple("Healthy", PillVariant.Ghost, c.accent)
    }

    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lists_source_row_${entity.id}"),
        accent = entity.enabled && attention == null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sourceShortName(entity.url),
                        style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.text,
                        maxLines = 1,
                    )
                    Text(
                        text = entity.url,
                        style = AhTheme.text.monoData,
                        color = c.textMute,
                        maxLines = 1,
                    )
                }
                Pill(text = statusText, variant = statusVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMeta(entity, df),
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                )
                PillSwitch(
                    checked = entity.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("lists_source_enabled_${entity.id}"),
                )
            }
            entity.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.border2),
                )
                Text(
                    text = "↳ $err",
                    style = AhTheme.text.monoCaption,
                    color = c.danger,
                    modifier = Modifier.testTag("lists_source_error_${entity.id}"),
                )
            }
            // Keep delete affordance available; existing tests rely on it.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = stringResource(R.string.lists_delete),
                    style = AhTheme.text.pill,
                    color = c.danger,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .testTag("lists_source_delete_${entity.id}")
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            entity.lastResult?.let { r ->
                Text(
                    text = "${stringResource(R.string.lists_last_result)}: $r",
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                    modifier = Modifier.testTag("lists_source_result_${entity.id}"),
                )
            }
            attention?.let { a ->
                val detail = when (a) {
                    ListAttention.NeverFetched -> stringResource(R.string.lists_needs_update_detail_never)
                    ListAttention.RefreshFailed -> stringResource(R.string.lists_needs_update_detail_error)
                    ListAttention.Stale -> stringResource(R.string.lists_needs_update_detail_stale)
                }
                Text(
                    text = stringResource(R.string.lists_needs_update),
                    style = AhTheme.text.monoCaption,
                    color = statusColor,
                    modifier = Modifier.testTag("lists_source_needs_update_${entity.id}"),
                )
                Text(
                    text = detail,
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                    modifier = Modifier.testTag("lists_source_needs_update_detail_${entity.id}"),
                )
            }
        }
    }
}

private fun sourceShortName(url: String): String {
    val withoutScheme = url.substringAfter("://", url)
    val host = withoutScheme.substringBefore('/')
    return host.ifBlank { url }
}

private fun formatMeta(entity: AdlistSourceEntity, df: DateFormat): String {
    val ts = entity.lastSuccessAt?.let { df.format(Date(it)) } ?: "never"
    return "updated $ts"
}
