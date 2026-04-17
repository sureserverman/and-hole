package org.pihole.android.data.backup

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity
import org.pihole.android.data.db.entity.UpstreamResolverEntity

/**
 * Explicit user-managed JSON backup of ad list sources, custom rules, and local DNS records.
 * Runtime/volatile data (query logs, compiled snapshots, caches) is intentionally excluded.
 */
object DataBackup {

    const val FORMAT_VERSION: Int = 1

    suspend fun exportJson(db: AppDatabase): String {
        val adlists = db.adlistSourceDao().getAll()
        val rules = db.customRuleDao().getAll()
        val locals = db.localDnsRecordDao().getAll()
        val upstreamResolvers = db.upstreamResolverDao().getAllOrdered()
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put(
            "adlist_sources",
            JSONArray().apply {
                for (a in adlists) {
                    put(
                        JSONObject().apply {
                            put("url", a.url)
                            put("enabled", a.enabled)
                            put("etag", a.etag ?: JSONObject.NULL)
                            put("lastModified", a.lastModified ?: JSONObject.NULL)
                            put("lastRefreshStartedAt", a.lastRefreshStartedAt ?: JSONObject.NULL)
                            put("lastSuccessAt", a.lastSuccessAt ?: JSONObject.NULL)
                            put("lastResult", a.lastResult ?: JSONObject.NULL)
                            put("lastError", a.lastError ?: JSONObject.NULL)
                        },
                    )
                }
            },
        )
        root.put(
            "custom_rules",
            JSONArray().apply {
                for (r in rules) {
                    put(
                        JSONObject().apply {
                            put("kind", r.kind)
                            put("value", r.value)
                            put("enabled", r.enabled)
                            put("comment", r.comment ?: JSONObject.NULL)
                        },
                    )
                }
            },
        )
        root.put(
            "local_dns_records",
            JSONArray().apply {
                for (l in locals) {
                    put(
                        JSONObject().apply {
                            put("name", l.name)
                            put("type", l.type)
                            put("value", l.value)
                            put("ttl", l.ttl)
                            put("enabled", l.enabled)
                        },
                    )
                }
            },
        )
        root.put(
            "upstream_resolvers",
            JSONArray().apply {
                for (u in upstreamResolvers) {
                    put(
                        JSONObject().apply {
                            put("label", u.label)
                            put("host", u.host)
                            put("port", u.port)
                            put("tlsServerName", u.tlsServerName ?: JSONObject.NULL)
                            put("enabled", u.enabled)
                            put("sortOrder", u.sortOrder)
                        },
                    )
                }
            },
        )
        return root.toString(2)
    }

    suspend fun importJson(db: AppDatabase, json: String): Result<Unit> =
        runCatching {
            val root = JSONObject(json)
            require(root.optInt("formatVersion", 0) == FORMAT_VERSION) { "Unsupported backup formatVersion" }
            db.withTransaction {
                db.clearAllTables()
                val adArr = root.getJSONArray("adlist_sources")
                for (i in 0 until adArr.length()) {
                    val o = adArr.getJSONObject(i)
                    db.adlistSourceDao().insert(
                        AdlistSourceEntity(
                            id = 0,
                            url = o.getString("url"),
                            enabled = o.getBoolean("enabled"),
                            etag = if (o.isNull("etag")) null else o.getString("etag"),
                            lastModified = if (o.isNull("lastModified")) null else o.getString("lastModified"),
                            lastRefreshStartedAt = o.optLongOrNull("lastRefreshStartedAt"),
                            lastSuccessAt = o.optLongOrNull("lastSuccessAt"),
                            lastResult = if (o.isNull("lastResult")) null else o.getString("lastResult"),
                            lastError = if (o.isNull("lastError")) null else o.getString("lastError"),
                        ),
                    )
                }
                val ruleArr = root.getJSONArray("custom_rules")
                for (i in 0 until ruleArr.length()) {
                    val o = ruleArr.getJSONObject(i)
                    val now = System.currentTimeMillis()
                    db.customRuleDao().insert(
                        CustomRuleEntity(
                            id = 0,
                            kind = o.getString("kind"),
                            value = o.getString("value"),
                            enabled = o.getBoolean("enabled"),
                            comment = if (o.isNull("comment")) null else o.getString("comment"),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
                val locArr = root.getJSONArray("local_dns_records")
                for (i in 0 until locArr.length()) {
                    val o = locArr.getJSONObject(i)
                    db.localDnsRecordDao().insert(
                        LocalDnsRecordEntity(
                            id = 0,
                            name = o.getString("name"),
                            type = o.getInt("type"),
                            value = o.getString("value"),
                            ttl = o.getInt("ttl"),
                            enabled = o.getBoolean("enabled"),
                        ),
                    )
                }
                val upstreamArr = root.optJSONArray("upstream_resolvers") ?: JSONArray()
                for (i in 0 until upstreamArr.length()) {
                    val o = upstreamArr.getJSONObject(i)
                    db.upstreamResolverDao().insert(
                        UpstreamResolverEntity(
                            id = 0,
                            label = o.getString("label"),
                            host = o.getString("host"),
                            port = o.getInt("port"),
                            tlsServerName = if (o.isNull("tlsServerName")) null else o.getString("tlsServerName"),
                            enabled = o.optBoolean("enabled", true),
                            sortOrder = o.optInt("sortOrder", i),
                        ),
                    )
                }
            }
        }
}

private fun JSONObject.optLongOrNull(key: String): Long? =
    when {
        !has(key) || isNull(key) -> null
        else -> getLong(key)
    }
