package org.pihole.android.feature.diagnostics

data class DiagnosticsUiState(
    val buildSection: String,
    val prefsSection: String,
    val dataSection: String,
    val manifestSection: String,
    /** Short Tor runtime line for the top card (same source as full report). */
    val runtimeGlancePrimary: String,
    val runtimeGlanceSecondary: String?,
    val runtimeSection: String,
    val cheatSheetSection: String?,
    val fullReportText: String,
)
