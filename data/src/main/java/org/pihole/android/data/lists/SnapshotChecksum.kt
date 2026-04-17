package org.pihole.android.data.lists

import java.security.MessageDigest

object SnapshotChecksum {
    fun sha256Hex(domains: Collection<String>): String {
        val joined = domains.sorted().joinToString("\n")
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(joined.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}
