package org.pihole.android.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import org.pihole.android.core.designsystem.components.AhAppBar
import org.pihole.android.core.designsystem.theme.AhTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.prefs.AppPreferences

private val SettingsContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val SettingsSectionGap = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: AppPreferences,
    backupVm: SettingsBackupViewModel,
    onOpenDiagnostics: () -> Unit = {},
) {
    val activityContext = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(prefs))
    val upstreamVm: UpstreamPolicyViewModel =
        viewModel(
            factory =
                UpstreamPolicyViewModelFactory(
                    db = DatabaseProvider.get(activityContext),
                    prefs = prefs,
                ),
        )
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { backupVm.importBackupJson(it) }
        }

    LaunchedEffect(backupVm) {
        backupVm.backupEvents.collect { ev ->
            when (ev) {
                is SettingsBackupEvent.ShareUri -> {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, ev.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    activityContext.startActivity(Intent.createChooser(send, "Export backup"))
                }
                is SettingsBackupEvent.Message ->
                    Toast.makeText(activityContext, ev.text, Toast.LENGTH_LONG).show()
            }
        }
    }
    val bindAllIfaces by vm.bindAllIfaces.collectAsStateWithLifecycle()
    val autoStart by vm.autoStart.collectAsStateWithLifecycle()
    val torRuntimeMode by vm.torRuntimeMode.collectAsStateWithLifecycle()
    val retentionDays by vm.retentionDays.collectAsStateWithLifecycle()
    val maxRows by vm.maxRows.collectAsStateWithLifecycle()
    val upstreamState by upstreamVm.uiState.collectAsStateWithLifecycle()
    val dnsActions = LocalDnsListenerActions.current
    var draftId by remember { mutableStateOf<Long?>(null) }
    var draftLabel by remember { mutableStateOf("") }
    var draftHost by remember { mutableStateOf("") }
    var draftPort by remember { mutableStateOf("853") }
    var draftTlsName by remember { mutableStateOf("") }
    var draftEnabled by remember { mutableStateOf(true) }
    var resolverError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = AhTheme.colors.bg,
        topBar = {
            AhAppBar(
                title = "Settings",
                sub = "preferences · upstream · privacy",
                modifier = Modifier.testTag("settings_top_bar_title"),
            )
        },
    ) { innerPadding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsSectionGap),
        ) {
            Text("DNS listener", style = MaterialTheme.typography.titleSmall)
            Text(
                "Start the foreground service to listen on 127.0.0.1 (see Home for port). " +
                    "Use Stop when you no longer need loopback DNS.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.settings_oem_battery_title), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.settings_oem_battery_body),
                style = MaterialTheme.typography.bodySmall,
            )
            if (dnsActions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = dnsActions.startListener,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Start DNS listener")
                    }
                    Button(
                        onClick = dnsActions.stopListener,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Stop DNS listener")
                    }
                }
            } else {
                Text(
                    "DNS controls are unavailable in this build context.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text("Quick tools", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("settings_open_diagnostics"),
                ) {
                    Text("Open diagnostics")
                }
            }

            Text("Tor runtime", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Use Tor for upstream DNS",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = upstreamState.useTor,
                    onCheckedChange = upstreamVm::setUseTor,
                    modifier = Modifier.testTag("settings_upstream_use_tor"),
                )
            }
            Text(
                "Choose whether DNS-over-Tor uses the embedded Arti runtime, the TorService compatibility runtime, or Auto fallback.",
                style = MaterialTheme.typography.bodySmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RuntimeModeButton(
                    label = "Auto",
                    selected = torRuntimeMode == AppPreferences.TOR_RUNTIME_MODE_AUTO,
                    enabled = upstreamState.useTor,
                    onClick = { vm.setTorRuntimeMode(AppPreferences.TOR_RUNTIME_MODE_AUTO) },
                )
                RuntimeModeButton(
                    label = "Embedded only",
                    selected = torRuntimeMode == AppPreferences.TOR_RUNTIME_MODE_EMBEDDED,
                    enabled = upstreamState.useTor,
                    onClick = { vm.setTorRuntimeMode(AppPreferences.TOR_RUNTIME_MODE_EMBEDDED) },
                )
                RuntimeModeButton(
                    label = "Compatibility only",
                    selected = torRuntimeMode == AppPreferences.TOR_RUNTIME_MODE_COMPATIBILITY,
                    enabled = upstreamState.useTor,
                    onClick = { vm.setTorRuntimeMode(AppPreferences.TOR_RUNTIME_MODE_COMPATIBILITY) },
                )
            }
            if (!upstreamState.useTor) {
                Text(
                    "Tor runtime selection applies only when 'Use Tor for upstream DNS' is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text("Upstream resolvers (DoT)", style = MaterialTheme.typography.titleSmall)
            if (upstreamState.resolvers.isEmpty()) {
                Text("No resolvers configured.", style = MaterialTheme.typography.bodySmall)
            } else {
                upstreamState.resolvers.forEachIndexed { index, resolver ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${index + 1}. ${resolver.label} — ${resolver.host}:${resolver.port}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = {
                                draftId = resolver.id
                                draftLabel = resolver.label
                                draftHost = resolver.host
                                draftPort = resolver.port.toString()
                                draftTlsName = resolver.tlsServerName.orEmpty()
                                draftEnabled = resolver.enabled
                                resolverError = null
                            }) { Text("Edit") }
                            OutlinedIconButton(
                                onClick = { upstreamVm.moveUp(resolver.id) },
                                modifier = Modifier.testTag("settings_resolver_move_up_${resolver.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                )
                            }
                            OutlinedIconButton(
                                onClick = { upstreamVm.moveDown(resolver.id) },
                                modifier = Modifier.testTag("settings_resolver_move_down_${resolver.id}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                )
                            }
                            OutlinedButton(onClick = { upstreamVm.delete(resolver.id) }) { Text("Delete") }
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = resolver.enabled,
                                onCheckedChange = { upstreamVm.setResolverEnabled(resolver.id, it) },
                                modifier =
                                    Modifier
                                        .testTag("settings_resolver_enabled_${resolver.id}")
                                        .semantics { contentDescription = "Enabled" },
                            )
                        }
                    }
                }
            }
            Text(if (draftId == null) "Add resolver" else "Edit resolver", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = draftLabel,
                onValueChange = { draftLabel = it },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draftHost,
                onValueChange = { draftHost = it },
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draftPort,
                onValueChange = { draftPort = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draftTlsName,
                onValueChange = { draftTlsName = it },
                label = { Text("TLS server name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    resolverError =
                        upstreamVm.saveResolver(
                            id = draftId,
                            label = draftLabel,
                            host = draftHost,
                            portText = draftPort,
                            tlsServerName = draftTlsName,
                            enabled = draftEnabled,
                        )
                    if (resolverError == null) {
                        draftId = null
                        draftLabel = ""
                        draftHost = ""
                        draftPort = "853"
                        draftTlsName = ""
                        draftEnabled = true
                    }
                }) { Text(if (draftId == null) "Add resolver" else "Save resolver") }
                OutlinedButton(onClick = {
                    draftId = null
                    draftLabel = ""
                    draftHost = ""
                    draftPort = "853"
                    draftTlsName = ""
                    draftEnabled = true
                    resolverError = null
                }) { Text("Reset") }
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = draftEnabled,
                    onCheckedChange = { draftEnabled = it },
                    modifier =
                        Modifier
                            .testTag("settings_draft_resolver_enabled")
                            .semantics { contentDescription = "Enabled" },
                )
            }
            resolverError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Text(stringResource(R.string.settings_dns_bind_all_title), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_dns_bind_all_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = bindAllIfaces,
                    onCheckedChange = vm::setBindAllInterfaces,
                )
            }

            Text("Boot", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Start DNS listener automatically after device boot",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoStart,
                    onCheckedChange = vm::setAutoStart,
                )
            }

            Text("Query log retention", style = MaterialTheme.typography.titleSmall)
            Text(
                "Control how long query logs are kept on-device. Lower values reduce storage usage.",
                style = MaterialTheme.typography.bodySmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { vm.setRetentionDays(retentionDays - 1) },
                        modifier = Modifier.testTag("settings_retention_days_minus"),
                    ) { Text("−") }
                    Text(
                        "Keep days: $retentionDays (0 = forever)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.setRetentionDays(retentionDays + 1) },
                        modifier = Modifier.testTag("settings_retention_days_plus"),
                    ) { Text("+") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { vm.setMaxRows(maxRows - 1000) },
                        modifier = Modifier.testTag("settings_log_max_rows_minus"),
                    ) { Text("−") }
                    Text(
                        "Max rows: $maxRows (0 = unlimited)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.setMaxRows(maxRows + 1000) },
                        modifier = Modifier.testTag("settings_log_max_rows_plus"),
                    ) { Text("+") }
                }
            }

            Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.settings_backup_body), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { backupVm.exportBackupJson() },
                    modifier = Modifier.testTag("settings_export_backup"),
                ) {
                    Text(stringResource(R.string.settings_export))
                }
                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "application/*", "*/*"))
                    },
                    modifier = Modifier.testTag("settings_import_backup"),
                ) {
                    Text(stringResource(R.string.settings_import))
                }
            }
        }
    }
}

@Composable
private fun RuntimeModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val text = if (selected) "$label (selected)" else label
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}
