package org.pihole.android.core.filter.rules

import org.pihole.android.core.filter.normalize.DomainNormalizer
import org.pihole.android.core.filter.trie.SuffixTrie
import java.util.regex.Pattern

class CompiledMatcher(
    private val exactAllow: Set<String>,
    private val exactDeny: Set<String>,
    private val subscribedTrie: SuffixTrie,
    private val regexAllow: List<Pattern>,
    private val regexDeny: List<Pattern>,
) {

    fun match(qname: String): FilterDecision {
        val fqdn = DomainNormalizer.normalizeFqdn(qname)
        if (fqdn in exactAllow) return FilterDecision.Allow
        for (p in regexAllow) {
            if (p.matcher(fqdn).matches()) return FilterDecision.Allow
        }
        if (fqdn in exactDeny) return FilterDecision.Block(RuleKind.EXACT_DENY)
        val rev = reversedLabels(fqdn)
        if (subscribedTrie.containsSuffix(rev)) {
            return FilterDecision.Block(RuleKind.SUBSCRIBED_DENY)
        }
        for (p in regexDeny) {
            if (p.matcher(fqdn).matches()) return FilterDecision.Block(RuleKind.REGEX_DENY)
        }
        return FilterDecision.Pass
    }

    private fun reversedLabels(fqdn: String): List<String> {
        val withoutDot = fqdn.removeSuffix(".")
        if (withoutDot.isEmpty()) return emptyList()
        return withoutDot.split('.').asReversed()
    }

    companion object {
        fun empty(): CompiledMatcher = CompiledMatcher(
            exactAllow = emptySet(),
            exactDeny = emptySet(),
            subscribedTrie = SuffixTrie(),
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
    }
}
