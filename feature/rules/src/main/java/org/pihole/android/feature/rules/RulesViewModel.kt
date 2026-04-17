package org.pihole.android.feature.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity
import org.pihole.android.data.rules.CustomRuleUpsert

class RulesViewModel(
    app: Application,
    private val db: AppDatabase,
) : AndroidViewModel(app) {

    val rules = db.customRuleDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val localRecords = db.localDnsRecordDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun addExactRule(isAllow: Boolean, rawDomain: String) = viewModelScope.launch {
        val kind = if (isAllow) "exact_allow" else "exact_deny"
        CustomRuleUpsert.upsertExactKind(
            db.customRuleDao(),
            kind = kind,
            rawValue = rawDomain,
            comment = "from rules UI",
        )
    }

    fun addRegexRule(isAllow: Boolean, pattern: String) = viewModelScope.launch {
        val kind = if (isAllow) "regex_allow" else "regex_deny"
        CustomRuleUpsert.upsertRegexKind(
            db.customRuleDao(),
            kind = kind,
            pattern = pattern,
            comment = "from rules UI",
        )
    }

    fun setRuleEnabled(rule: CustomRuleEntity, enabled: Boolean) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.customRuleDao().update(rule.copy(enabled = enabled, updatedAt = now))
    }

    fun deleteRule(rule: CustomRuleEntity) = viewModelScope.launch {
        db.customRuleDao().deleteById(rule.id)
    }

    fun addLocalRecord(name: String, qtype: Int, value: String, ttl: Int) = viewModelScope.launch {
        db.localDnsRecordDao().insert(
            LocalDnsRecordEntity(
                name = name.trim(),
                type = qtype,
                value = value.trim(),
                ttl = ttl.coerceIn(30, 86_400),
                enabled = true,
            ),
        )
    }

    fun setLocalRecordEnabled(record: LocalDnsRecordEntity, enabled: Boolean) = viewModelScope.launch {
        db.localDnsRecordDao().update(record.copy(enabled = enabled))
    }

    fun deleteLocalRecord(record: LocalDnsRecordEntity) = viewModelScope.launch {
        db.localDnsRecordDao().deleteById(record.id)
    }

    companion object {
        val QTYPE_CHOICES = listOf(
            DnsConstants.QTYPE_A to "A (IPv4)",
            DnsConstants.QTYPE_AAAA to "AAAA (IPv6)",
        )
    }
}
