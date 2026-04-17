package org.pihole.android.data.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.filter.rules.FilterDecision

class MatcherAssemblerTest {

    @Test
    fun fromSubscribedOnly_skipsSingleLabel_soLocalPseudoTldNotBlocked() {
        val m = MatcherAssembler.fromSubscribedOnly(listOf("local.", "evil.ads.com."))
        assertEquals(FilterDecision.Pass, m.match("test.pi-hole.local."))
        assertTrue(m.match("evil.ads.com.") is FilterDecision.Block)
    }
}
