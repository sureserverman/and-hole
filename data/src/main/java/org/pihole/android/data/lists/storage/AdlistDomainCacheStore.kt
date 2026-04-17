package org.pihole.android.data.lists.storage

import android.content.Context
import org.pihole.android.data.lists.AdlistSnapshotManifest
import java.io.File

/**
 * Per-source domain cache for HTTP 304 / not-modified paths. Too large for a single Room row on Android.
 */
object AdlistDomainCacheStore {
    private const val DIR = "adlist-cache"

    private fun file(context: Context, sourceId: Long): File =
        File(File(context.filesDir, DIR), "$sourceId.json")

    fun read(context: Context, sourceId: Long): Set<String>? {
        val f = file(context, sourceId)
        if (!f.isFile || f.length() == 0L) return null
        return AdlistSnapshotManifest.parseDomainArray(f.readText())
    }

    fun write(context: Context, sourceId: Long, domains: Collection<String>) {
        val dir = File(context.filesDir, DIR)
        dir.mkdirs()
        val json = AdlistSnapshotManifest.serializeDomainArray(domains)
        val out = file(context, sourceId)
        val tmp = File(dir, "$sourceId.json.tmp")
        tmp.writeText(json)
        if (out.exists()) out.delete()
        check(tmp.renameTo(out)) { "failed to finalize adlist cache" }
    }

    fun delete(context: Context, sourceId: Long) {
        file(context, sourceId).delete()
    }
}
