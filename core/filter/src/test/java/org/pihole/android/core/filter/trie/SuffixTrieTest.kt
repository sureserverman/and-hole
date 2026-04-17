package org.pihole.android.core.filter.trie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuffixTrieTest {

    @Test
    fun subdomainMatchesBlockedParent() {
        val t = SuffixTrie()
        t.insertReversedLabels(listOf("com", "example", "ads"))
        assertTrue(t.containsSuffix(listOf("com", "example", "ads", "evil")))
    }

    @Test
    fun unrelatedDomainDoesNotMatch() {
        val t = SuffixTrie()
        t.insertReversedLabels(listOf("com", "example", "ads"))
        assertFalse(t.containsSuffix(listOf("com", "other", "x")))
    }
}
