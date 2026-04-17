package org.pihole.android.feature.logs

import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.filter.rules.CompiledMatcher
import org.pihole.android.core.filter.rules.FilterDecision
import org.pihole.android.core.filter.trie.SuffixTrie

class LogsActionTest {

    @Test
    fun allowRuleRefreshesMatcher() {
        val m = CompiledMatcher(
            exactAllow = setOf("x.com."),
            exactDeny = emptySet(),
            subscribedTrie = SuffixTrie(),
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
        assertTrue(m.match("x.com.") is FilterDecision.Allow)
    }

    @Test
    fun denyRuleRefreshesMatcher() {
        val m = CompiledMatcher(
            exactAllow = emptySet(),
            exactDeny = setOf("y.com."),
            subscribedTrie = SuffixTrie(),
            regexAllow = emptyList(),
            regexDeny = emptyList(),
        )
        assertTrue(m.match("y.com.") is FilterDecision.Block)
    }
}
