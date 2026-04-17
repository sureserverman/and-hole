package org.pihole.android.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class SectionRow(
    val title: String,
    val body: String,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    vm: DiagnosticsViewModel,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val sections =
        buildList {
            add(SectionRow("Build", state.buildSection, "diagnostics_section_build"))
            add(SectionRow("Preferences", state.prefsSection, "diagnostics_section_prefs"))
            add(SectionRow("Data (Room)", state.dataSection, "diagnostics_section_data"))
            add(SectionRow("On-disk manifest", state.manifestSection, "diagnostics_section_manifest"))
            add(SectionRow("Runtime", state.runtimeSection, "diagnostics_section_runtime"))
            state.cheatSheetSection?.let { cheat ->
                add(SectionRow("Debug cheat sheet (host / adb)", cheat, "diagnostics_section_cheatsheet"))
            }
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Diagnostics", modifier = Modifier.testTag("diagnostics_top_bar_title")) },
                actions = {
                    TextButton(
                        onClick = { vm.copyReportToClipboard() },
                        modifier = Modifier.testTag("diagnostics_copy_report"),
                    ) {
                        Text("Copy report")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("diagnostics_scroll"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("diagnostics_runtime_glance_card"),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Tor runtime (now)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.runtimeGlancePrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("diagnostics_tor_runtime_mode"),
                        )
                        state.runtimeGlanceSecondary?.let { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(sections, key = { it.testTag }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(row.testTag),
                    )
                    Text(
                        text = row.body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
