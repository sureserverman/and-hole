package org.pihole.android.data.lists

import org.pihole.android.core.filter.normalize.DomainNormalizer
import org.pihole.android.core.filter.rules.CompiledMatcher
import org.pihole.android.core.filter.trie.SuffixTrie
import org.pihole.android.data.db.entity.CustomRuleEntity
import java.util.regex.Pattern

object MatcherAssembler {
    fun fromSubscribedOnly(domains: Collection<String>): CompiledMatcher =
        assemble(domains, emptyList())

    fun assemble(
        subscribedFqdns: Collection<String>,
        customRules: List<CustomRuleEntity>,
    ): CompiledMatcher {
        val exactAllow = mutableSetOf<String>()
        val exactDeny = mutableSetOf<String>()
        val regexAllow = mutableListOf<Pattern>()
        val regexDeny = mutableListOf<Pattern>()
        for (r in customRules) {
            if (!r.enabled) continue
            when (r.kind) {
                "exact_allow" -> {
                    val fqdn = DomainNormalizer.normalizeFqdn(r.value)
                    exactAllow.add(fqdn)
                }
                "exact_deny" -> {
                    val fqdn = DomainNormalizer.normalizeFqdn(r.value)
                    exactDeny.add(fqdn)
                }
                "regex_allow" -> compilePatternOrNull(r.value)?.let { regexAllow.add(it) }
                "regex_deny" -> compilePatternOrNull(r.value)?.let { regexDeny.add(it) }
            }
        }
        val trie = buildSubscribedTrie(subscribedFqdns)
        return CompiledMatcher(
            exactAllow = exactAllow,
            exactDeny = exactDeny,
            subscribedTrie = trie,
            regexAllow = regexAllow,
            regexDeny = regexDeny,
        )
    }

    private fun compilePatternOrNull(source: String): Pattern? =
        runCatching { Pattern.compile(source.trim()) }.getOrNull()

    private fun buildSubscribedTrie(domains: Collection<String>): SuffixTrie {
        val trie = SuffixTrie()
        for (d in domains) {
            val fqdn = d.trim().lowercase().let { if (it.endsWith(".")) it else "$it." }
            val withoutDot = fqdn.removeSuffix(".")
            if (withoutDot.isEmpty()) continue
            // Single-label rows (e.g. "local" from bad list data) would mark the trie root child
            // terminal and falsely block entire pseudo-TLDs such as *.local.
            if (!withoutDot.contains('.')) continue
            val rev = withoutDot.split('.').asReversed()
            trie.insertReversedLabels(rev)
        }
        return trie
    }
}
