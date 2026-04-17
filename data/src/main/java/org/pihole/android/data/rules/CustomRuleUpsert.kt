package org.pihole.android.data.rules

import org.pihole.android.core.filter.normalize.DomainNormalizer
import org.pihole.android.data.db.dao.CustomRuleDao
import org.pihole.android.data.db.entity.CustomRuleEntity
import java.util.regex.Pattern

object CustomRuleUpsert {

    /**
     * Insert or update an exact_allow / exact_deny rule. [rawValue] is trimmed and normalized to FQDN form.
     * No-op if the domain is empty after normalization.
     */
    suspend fun upsertExactKind(
        dao: CustomRuleDao,
        kind: String,
        rawValue: String,
        comment: String,
    ) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return
        val fqdn = DomainNormalizer.normalizeFqdn(trimmed)
        if (fqdn.removeSuffix(".").isBlank()) return

        val now = System.currentTimeMillis()
        val existing = dao.findByKindAndValue(kind, fqdn)
        if (existing != null) {
            if (!existing.enabled || existing.comment != comment) {
                dao.update(
                    existing.copy(
                        enabled = true,
                        comment = comment,
                        updatedAt = now,
                    ),
                )
            }
            return
        }
        dao.insert(
            CustomRuleEntity(
                kind = kind,
                value = fqdn,
                enabled = true,
                comment = comment,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Insert or update a regex_allow / regex_deny rule. [pattern] must be valid [Pattern] syntax.
     */
    suspend fun upsertRegexKind(
        dao: CustomRuleDao,
        kind: String,
        pattern: String,
        comment: String,
    ) {
        val p = pattern.trim()
        if (p.isEmpty()) return
        runCatching { Pattern.compile(p) }.getOrElse { return }

        val now = System.currentTimeMillis()
        val existing = dao.findByKindAndValue(kind, p)
        if (existing != null) {
            if (!existing.enabled || existing.comment != comment) {
                dao.update(
                    existing.copy(
                        enabled = true,
                        comment = comment,
                        updatedAt = now,
                    ),
                )
            }
            return
        }
        dao.insert(
            CustomRuleEntity(
                kind = kind,
                value = p,
                enabled = true,
                comment = comment,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
