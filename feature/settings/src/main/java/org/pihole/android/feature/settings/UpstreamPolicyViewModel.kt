package org.pihole.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.data.runtime.DefaultUpstreamPolicyRepository
import org.pihole.android.data.runtime.UpstreamPolicyRepository
import org.pihole.android.data.runtime.UpstreamResolver

data class UpstreamPolicyUiState(
    val useTor: Boolean = true,
    val resolvers: List<UpstreamResolver> = emptyList(),
)

class UpstreamPolicyViewModel(
    private val prefs: AppPreferences,
    private val repository: UpstreamPolicyRepository,
) : ViewModel() {
    val uiState: StateFlow<UpstreamPolicyUiState> =
        combine(prefs.upstreamUseTor, repository.resolvers) { useTor, resolvers ->
            UpstreamPolicyUiState(useTor = useTor, resolvers = resolvers)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UpstreamPolicyUiState(),
        )

    fun setUseTor(enabled: Boolean) {
        viewModelScope.launch { prefs.setUpstreamUseTor(enabled) }
    }

    fun setResolverEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setResolverEnabled(id, enabled) }
    }

    fun moveUp(id: Long) {
        viewModelScope.launch { repository.moveResolverUp(id) }
    }

    fun moveDown(id: Long) {
        viewModelScope.launch { repository.moveResolverDown(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteResolver(id) }
    }

    fun saveResolver(
        id: Long?,
        label: String,
        host: String,
        portText: String,
        tlsServerName: String,
        enabled: Boolean,
    ): String? {
        val normalizedLabel = label.trim()
        val normalizedHost = host.trim()
        val normalizedTls = tlsServerName.trim()
        if (normalizedLabel.isEmpty()) return "Label is required"
        if (normalizedHost.isEmpty()) return "Host is required"
        val port = portText.toIntOrNull() ?: return "Port must be a number"
        if (port !in 1..65535) return "Port must be in range 1..65535"
        viewModelScope.launch {
            if (id == null) {
                repository.addResolver(
                    label = normalizedLabel,
                    host = normalizedHost,
                    port = port,
                    tlsServerName = normalizedTls.ifBlank { normalizedHost },
                    enabled = enabled,
                )
            } else {
                repository.updateResolver(
                    id = id,
                    label = normalizedLabel,
                    host = normalizedHost,
                    port = port,
                    tlsServerName = normalizedTls.ifBlank { normalizedHost },
                    enabled = enabled,
                )
            }
        }
        return null
    }
}

class UpstreamPolicyViewModelFactory(
    private val db: AppDatabase,
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpstreamPolicyViewModel::class.java)) {
            return UpstreamPolicyViewModel(
                prefs = prefs,
                repository = DefaultUpstreamPolicyRepository(db),
            ) as T
        }
        error("Unknown ViewModel class")
    }
}
