package org.pihole.android.feature.home.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.data.runtime.DnsControlRepository
import org.pihole.android.data.runtime.DnsForegroundRuntimeState
import org.pihole.android.data.prefs.AppPreferences

class SetupViewModel(
    private val repository: DnsControlRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<SetupUiState> =
        combine(
            repository.snapshot,
            prefs.onboardingCompleted,
            prefs.setupClientMode,
            prefs.setupBindAllInterfacesDismissed,
        ) { snapshot, onboardingCompleted, clientMode, bindDismissed ->
            val hasPort = snapshot.listenerPort > 0
            val modeSelected = clientMode.isNotBlank()
            val privateDnsShown = modeSelected
            val bindRecommendationShown = snapshot.bindAllInterfaces || bindDismissed
            val requiredStepsDone = hasPort && modeSelected && privateDnsShown

            val steps =
                listOf(
                    SetupStepState(
                        step = SetupStep.SERVICE_RUNNING,
                        complete = snapshot.listenerState == DnsForegroundRuntimeState.Running,
                        summary = "DNS listener is running",
                    ),
                    SetupStepState(
                        step = SetupStep.PORT_CONFIRMED,
                        complete = hasPort,
                        summary = "Listening port is known (${snapshot.listenerPort})",
                    ),
                    SetupStepState(
                        step = SetupStep.CLIENT_MODE_SELECTED,
                        complete = modeSelected,
                        summary = "Client mode selected",
                    ),
                    SetupStepState(
                        step = SetupStep.PRIVATE_DNS_GUIDANCE_SHOWN,
                        complete = privateDnsShown,
                        summary = "Private DNS guidance shown",
                    ),
                    SetupStepState(
                        step = SetupStep.BIND_ALL_INTERFACES_RECOMMENDATION_SHOWN,
                        complete = bindRecommendationShown,
                        summary = "Bind-all recommendation acknowledged",
                    ),
                )

            SetupUiState(
                showOnboarding = !onboardingCompleted,
                selectedClientMode = clientMode,
                steps = steps,
                generatedConfig = generatedConfig(clientMode, snapshot.listenerPort),
                recommendedActions = buildList {
                    if (snapshot.listenerState != DnsForegroundRuntimeState.Running) {
                        add("Start DNS listener from Home controls")
                    }
                    if (!modeSelected) add("Select a client mode")
                    if (!bindRecommendationShown) add("If loopback fails for VPN clients, enable bind-all interfaces")
                    if (requiredStepsDone && !onboardingCompleted) add("Mark onboarding complete")
                },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SetupUiState(),
        )

    fun selectClientMode(mode: String) {
        viewModelScope.launch { prefs.setSetupClientMode(mode) }
    }

    fun dismissBindAllInterfacesRecommendation() {
        viewModelScope.launch { prefs.setSetupBindAllInterfacesDismissed(true) }
    }

    fun completeOnboardingIfReady() {
        val state = uiState.value
        val requiredDone =
            state.steps.firstOrNull { it.step == SetupStep.PORT_CONFIRMED }?.complete == true &&
                state.steps.firstOrNull { it.step == SetupStep.CLIENT_MODE_SELECTED }?.complete == true &&
                state.steps.firstOrNull { it.step == SetupStep.PRIVATE_DNS_GUIDANCE_SHOWN }?.complete == true
        if (!requiredDone) return
        viewModelScope.launch { prefs.setOnboardingCompleted(true) }
    }

    private fun generatedConfig(mode: String, port: Int): String =
        when (mode) {
            "sing_box" ->
                """
                sing-box DNS server:
                type: udp
                server: 127.0.0.1
                server_port: $port
                """.trimIndent()
            "android_private_dns" ->
                "Use Android Private DNS as Automatic/Off during local loopback testing. DNS listener port: $port."
            "other" ->
                "Point your client DNS to 127.0.0.1:$port using UDP."
            else -> "Select a client mode to generate setup instructions."
        }
}
