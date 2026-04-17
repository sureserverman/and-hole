package org.pihole.android.data.lists

import org.json.JSONArray
import org.json.JSONObject

object AdlistSnapshotManifest {
    private const val KEY_V = "v"
    private const val KEY_SUFFIX = "suffixDeny"
    private const val VERSION = 1

    fun serializeSuffixDenyDomains(domains: Collection<String>): String {
        val arr = JSONArray()
        domains.sorted().forEach { arr.put(it) }
        return JSONObject().put(KEY_V, VERSION).put(KEY_SUFFIX, arr).toString()
    }

    fun parseSuffixDenyDomains(manifestJson: String): Set<String> {
        val o = JSONObject(manifestJson)
        val arr = o.getJSONArray(KEY_SUFFIX)
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            out.add(arr.getString(i))
        }
        return out
    }

    /** Plain JSON array of normalized FQDN strings (per-source cache on 304). */
    fun serializeDomainArray(domains: Collection<String>): String {
        val arr = JSONArray()
        domains.sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    fun parseDomainArray(json: String): Set<String> {
        val arr = JSONArray(json)
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            out.add(arr.getString(i))
        }
        return out
    }
}
