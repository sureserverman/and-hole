package org.pihole.android.feature.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.rules.CustomRuleUpsert

enum class LogDecisionFilter {
    ALL,
    BLOCKED,
    ALLOWED,
    PASS,
}

sealed interface LogsUiEvent {
    data class ShareExport(val export: LogsExport) : LogsUiEvent
    data class ToastMessage(val message: String) : LogsUiEvent
}

class LogsViewModel(
    app: Application,
    private val appDb: AppDatabase,
) : AndroidViewModel(app) {

    private val appContext = app.applicationContext

    private val _events = MutableSharedFlow<LogsUiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val rawLogs = appDb.queryLogDao().observeRecent(200)
    private val _searchQuery = MutableStateFlow("")
    private val _decisionFilter = MutableStateFlow(LogDecisionFilter.ALL)

    val searchQuery = _searchQuery.asStateFlow()
    val decisionFilter = _decisionFilter.asStateFlow()

    val visibleLogs =
        combine(rawLogs, _searchQuery, _decisionFilter) { list, q, filter ->
            val needle = q.trim()
            list.filter { row ->
                val matchesSearch = needle.isEmpty() || row.qname.contains(needle, ignoreCase = true)
                val matchesDecision =
                    when (filter) {
                        LogDecisionFilter.ALL -> true
                        LogDecisionFilter.BLOCKED -> row.decision == "blocked"
                        LogDecisionFilter.ALLOWED -> row.decision == "allowed"
                        LogDecisionFilter.PASS -> row.decision == "pass"
                    }
                matchesSearch && matchesDecision
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList(),
        )

    val insights =
        rawLogs.map { rows -> computeInsights(rows) }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            LogsInsightsUiState(),
        )

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun setDecisionFilter(filter: LogDecisionFilter) {
        _decisionFilter.value = filter
    }

    fun export(format: LogsExportFormat, limit: Int = 5_000) = viewModelScope.launch {
        val rows = appDb.queryLogDao().listRecent(limit)
        if (rows.isEmpty()) {
            _events.tryEmit(LogsUiEvent.ToastMessage("No logs to export yet"))
            return@launch
        }
        val export = LogsExportWriter.write(appContext, format, rows)
        _events.tryEmit(LogsUiEvent.ShareExport(export))
    }

    fun clearLogs() = viewModelScope.launch {
        val deleted = appDb.queryLogDao().deleteAll()
        _events.tryEmit(LogsUiEvent.ToastMessage("Cleared $deleted log entries"))
    }

    fun addExactAllowFromBlocked(qname: String) = viewModelScope.launch {
        CustomRuleUpsert.upsertExactKind(
            appDb.customRuleDao(),
            kind = "exact_allow",
            rawValue = qname,
            comment = "from log (allow)",
        )
    }

    fun addExactDenyFromAllowed(qname: String) = viewModelScope.launch {
        CustomRuleUpsert.upsertExactKind(
            appDb.customRuleDao(),
            kind = "exact_deny",
            rawValue = qname,
            comment = "from log (block)",
        )
    }

    companion object {
        internal fun computeInsights(rows: List<org.pihole.android.data.db.entity.QueryLogEntity>): LogsInsightsUiState {
            val decisionCounts =
                rows.groupingBy { it.decision }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { InsightDecisionStat(decision = it.key, hits = it.value) }

            val topBlocked =
                rows.filter { it.decision == "blocked" }
                    .groupingBy { it.qname }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { InsightDomainStat(qname = it.key, hits = it.value) }

            val topAllowed =
                rows.filter { it.decision == "allowed" }
                    .groupingBy { it.qname }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { InsightDomainStat(qname = it.key, hits = it.value) }

            return LogsInsightsUiState(
                decisionCounts = decisionCounts,
                topBlockedDomains = topBlocked,
                topAllowedDomains = topAllowed,
                cacheHits = rows.count { it.answeredFromCache },
                upstreamPasses = rows.count { it.decision == "pass" },
            )
        }
    }
}
