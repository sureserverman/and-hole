package org.pihole.android.core.filter.normalize

object DomainNormalizer {
    fun normalizeFqdn(input: String): String {
        val t = input.trim().lowercase()
        return if (t.endsWith(".")) t else "$t."
    }
}
