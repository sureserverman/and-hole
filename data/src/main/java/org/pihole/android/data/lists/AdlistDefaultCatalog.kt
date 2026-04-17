package org.pihole.android.data.lists

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.AdlistSourceEntity

/**
 * Merges URLs from [ASSET_NAME] into [adlist_sources]: any catalog URL not already present is inserted.
 * Export from And-hole `gravity.db` via [scripts/export_gravity_adlists.py], rebuild, and launch — existing
 * rows are kept; only missing URLs are added.
 */
object AdlistDefaultCatalog {

    const val ASSET_NAME = "default_adlists.json"

    data class Entry(val url: String, val enabled: Boolean)

    fun parseJson(json: String): List<Entry> {
        val arr = JSONArray(json)
        val out = ArrayList<Entry>(arr.length())
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i) as? JSONObject ?: continue
            val url = o.optString("url").ifBlank { o.optString("address") }.trim()
            if (url.isEmpty()) continue
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                continue
            }
            val enabled = if (o.has("enabled")) o.getBoolean("enabled") else true
            val key = url.lowercase()
            if (!seen.add(key)) continue
            out.add(Entry(url, enabled))
        }
        return out
    }

    suspend fun mergeDefaultAdlistsFromAssets(context: Context, db: AppDatabase) {
        val raw =
            try {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                return
            }
        val rows = parseJson(raw)
        if (rows.isEmpty()) return

        db.withTransaction {
            val dao = db.adlistSourceDao()
            val existingLower = dao.listAllUrls().mapTo(HashSet()) { it.lowercase() }
            val toInsert = rows.filter { it.url.lowercase() !in existingLower }
            if (toInsert.isEmpty()) return@withTransaction
            dao.insertAll(
                toInsert.map { e ->
                    AdlistSourceEntity(
                        url = e.url,
                        enabled = e.enabled,
                        etag = null,
                        lastModified = null,
                        lastRefreshStartedAt = null,
                        lastSuccessAt = null,
                        lastResult = null,
                        lastError = null,
                    )
                },
            )
        }
    }
}
