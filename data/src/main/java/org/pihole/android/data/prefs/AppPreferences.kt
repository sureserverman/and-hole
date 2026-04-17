package org.pihole.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {

    private val dnsPort = intPreferencesKey("dns_listen_port")
    private val logRetentionDays = intPreferencesKey("log_retention_days")
    private val logMaxRows = intPreferencesKey("log_max_rows")
    private val autoStart = booleanPreferencesKey("auto_start_service")
    /** When true, DNS binds 0.0.0.0 (all interfaces) so VPN/tun clients can reach the port; default loopback-only. */
    private val dnsBindAllInterfacesKey = booleanPreferencesKey("dns_bind_all_interfaces")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val setupClientModeKey = stringPreferencesKey("setup_client_mode")
    private val setupBindAllInterfacesDismissedKey = booleanPreferencesKey("setup_bind_all_interfaces_dismissed")
    private val torRuntimeModeKey = stringPreferencesKey("tor_runtime_mode")
    private val upstreamUseTorKey = booleanPreferencesKey("upstream_use_tor")

    val dnsListenPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[dnsPort] ?: DEFAULT_DNS_PORT
    }

    val logRetentionDaysFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[logRetentionDays] ?: DEFAULT_LOG_RETENTION_DAYS
    }

    val logMaxRowsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[logMaxRows] ?: DEFAULT_LOG_MAX_ROWS
    }

    val autoStartEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoStart] ?: false
    }

    val dnsBindAllInterfaces: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dnsBindAllInterfacesKey] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingCompletedKey] ?: false
    }

    val setupClientMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[setupClientModeKey] ?: ""
    }

    val setupBindAllInterfacesDismissed: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[setupBindAllInterfacesDismissedKey] ?: false
    }

    val torRuntimeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[torRuntimeModeKey] ?: TOR_RUNTIME_MODE_AUTO
    }

    val upstreamUseTor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[upstreamUseTorKey] ?: true
    }

    suspend fun setDnsListenPort(port: Int) {
        context.dataStore.edit { it[dnsPort] = port }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dataStore.edit { it[logRetentionDays] = days }
    }

    suspend fun setLogMaxRows(rows: Int) {
        context.dataStore.edit { it[logMaxRows] = rows }
    }

    suspend fun setAutoStartEnabled(enabled: Boolean) {
        context.dataStore.edit { it[autoStart] = enabled }
    }

    suspend fun setDnsBindAllInterfaces(enabled: Boolean) {
        context.dataStore.edit { it[dnsBindAllInterfacesKey] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[onboardingCompletedKey] = completed }
    }

    suspend fun setSetupClientMode(mode: String) {
        context.dataStore.edit { it[setupClientModeKey] = mode }
    }

    suspend fun setSetupBindAllInterfacesDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[setupBindAllInterfacesDismissedKey] = dismissed }
    }

    suspend fun setTorRuntimeMode(mode: String) {
        context.dataStore.edit { it[torRuntimeModeKey] = mode }
    }

    suspend fun setUpstreamUseTor(enabled: Boolean) {
        context.dataStore.edit { it[upstreamUseTorKey] = enabled }
    }

    companion object {
        const val DEFAULT_DNS_PORT: Int = 53_535
        const val DEFAULT_LOG_RETENTION_DAYS: Int = 7
        const val DEFAULT_LOG_MAX_ROWS: Int = 10_000
        const val TOR_RUNTIME_MODE_AUTO: String = "auto"
        const val TOR_RUNTIME_MODE_EMBEDDED: String = "embedded"
        const val TOR_RUNTIME_MODE_COMPATIBILITY: String = "compatibility"
    }
}
