package org.pihole.android.data.lists

import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.filter.rules.FilterDecision
import org.pihole.android.data.db.entity.CustomRuleEntity

class MatcherAssemblerRegexTest {

    @Test
    fun regexDeny_matchesPattern() {
        val now = 1L
        val rules =
            listOf(
                CustomRuleEntity(
                    id = 1,
                    kind = "regex_deny",
                    value = ".*badstuff.*",
                    enabled = true,
                    comment = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        val m = MatcherAssembler.assemble(emptyList(), rules)
        assertTrue(m.match("foo.badstuff.bar.") is FilterDecision.Block)
    }

    @Test
    fun regexAllow_overridesDenyTrieOrder() {
        val now = 1L
        val rules =
            listOf(
                CustomRuleEntity(1, "regex_allow", ".*okbad.*", true, null, now, now),
                CustomRuleEntity(2, "regex_deny", ".*okbad.*", true, null, now, now),
            )
        val m = MatcherAssembler.assemble(listOf("evil.com."), rules)
        // exact allow runs before regex allow; regex allow before exact deny — both regex: first regex_allow wins
        assertTrue(m.match("x.okbad.y.") is FilterDecision.Allow)
    }
}
