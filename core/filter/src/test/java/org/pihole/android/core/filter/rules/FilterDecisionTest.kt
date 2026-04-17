package org.pihole.android.core.filter.rules

import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.filter.trie.SuffixTrie
import java.util.regex.Pattern

class FilterDecisionTest {

    @Test
    fun exactAllow_beatsExactDeny() {
        val m = CompiledMatcher(
            exactAllow = setOf("a.com."),
            exactDeny = setOf("a.com."),
            subscribedTrie = SuffixTrie(),
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
        assertTrue(m.match("a.com.") is FilterDecision.Allow)
    }

    @Test
    fun exactDeny_beatsSubscribed() {
        val trie = SuffixTrie()
        trie.insertReversedLabels(listOf("com", "site", "bad"))
        val m = CompiledMatcher(
            exactAllow = emptySet(),
            exactDeny = setOf("other.site.com."),
            subscribedTrie = trie,
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
        assertTrue(m.match("other.site.com.") is FilterDecision.Block)
    }

    @Test
    fun regexAllow_beatsExactDeny_whenMatched() {
        val m = CompiledMatcher(
            exactAllow = emptySet(),
            exactDeny = setOf("x.com."),
            subscribedTrie = SuffixTrie(),
            regexAllow = listOf(Pattern.compile("^x\\.com\\.$")),
            regexDeny = emptyList(),
        )
        assertTrue(m.match("x.com.") is FilterDecision.Allow)
    }
}
