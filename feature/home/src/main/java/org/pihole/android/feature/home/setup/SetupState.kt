package org.pihole.android.feature.home.setup

data class SetupStepState(
    val step: SetupStep,
    val complete: Boolean,
    val summary: String,
)

data class SetupUiState(
    val showOnboarding: Boolean = false,
    val selectedClientMode: String = "",
    val steps: List<SetupStepState> = emptyList(),
    val generatedConfig: String = "",
    val recommendedActions: List<String> = emptyList(),
)
