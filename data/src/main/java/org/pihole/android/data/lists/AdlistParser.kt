package org.pihole.android.data.lists

import org.pihole.android.core.filter.normalize.DomainNormalizer

object AdlistParser {

    fun parseHostsStyle(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val host = parts[1].trim()
            if (host.isEmpty() || host == "localhost") continue
            out.add(DomainNormalizer.normalizeFqdn(host))
        }
        return out
    }
}
