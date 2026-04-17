package org.pihole.android.data.lists

import android.content.Context
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.CompiledSnapshotEntity
import org.pihole.android.data.lists.storage.AdlistDomainCacheStore
import org.pihole.android.data.lists.storage.CompiledSnapshotManifestStore

object AdlistRefreshEngine {

    suspend fun refreshAll(context: Context, db: AppDatabase) {
        val sourceDao = db.adlistSourceDao()
        val sources = sourceDao.getEnabled()
        val merged = LinkedHashSet<String>()
        val downloader = AdlistDownloader()
        val now = System.currentTimeMillis()

        for (src in sources) {
            try {
                sourceDao.update(
                    src.copy(
                        lastRefreshStartedAt = now,
                        lastError = null,
                    ),
                )
                val result = downloader.download(src.url, src.etag, src.lastModified)
                val domains: Set<String> =
                    if (result.notModified) {
                        AdlistDomainCacheStore.read(context, src.id) ?: emptySet()
                    } else {
                        AdlistParser.parseHostsStyle(result.body)
                    }
                merged.addAll(domains)
                if (!result.notModified) {
                    AdlistDomainCacheStore.write(context, src.id, domains)
                }
                sourceDao.update(
                    src.copy(
                        etag = result.etag ?: src.etag,
                        lastModified = result.lastModified ?: src.lastModified,
                        lastSuccessAt = now,
                        lastResult = if (result.notModified) "not_modified" else "ok",
                        lastError = null,
                    ),
                )
            } catch (e: Exception) {
                sourceDao.update(
                    src.copy(
                        lastError = e.message ?: e.javaClass.simpleName,
                        lastResult = "error",
                    ),
                )
            }
        }

        persistCompiledSnapshot(context, db, merged)
    }

    private suspend fun persistCompiledSnapshot(context: Context, db: AppDatabase, merged: Set<String>) {
        val customRules = db.customRuleDao().getAll()
        val exactAllow = customRules.count { it.enabled && it.kind == "exact_allow" }
        val exactDeny = customRules.count { it.enabled && it.kind == "exact_deny" }
        val manifest = AdlistSnapshotManifest.serializeSuffixDenyDomains(merged)
        val checksum = SnapshotChecksum.sha256Hex(merged)
        CompiledSnapshotManifestStore.write(context, manifest)
        db.compiledSnapshotDao().insert(
            CompiledSnapshotEntity(
                createdAt = System.currentTimeMillis(),
                ruleCountExactAllow = exactAllow,
                ruleCountExactDeny = exactDeny,
                ruleCountSuffixDeny = merged.size,
                ruleCountRegexAllow = 0,
                ruleCountRegexDeny = 0,
                checksum = checksum,
            ),
        )
    }
}
