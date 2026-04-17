package org.pihole.android.data.lists

import org.junit.Assert.assertTrue
import org.junit.Test
import org.pihole.android.core.filter.rules.FilterDecision

class SnapshotCompilerTest {

    @Test
    fun compiledMatcher_blocksDomain() {
        val m = SnapshotCompiler.compile(listOf("evil.ads.example.com"))
        val d = m.match("evil.ads.example.com.")
        assertTrue(d is FilterDecision.Block)
    }
}
