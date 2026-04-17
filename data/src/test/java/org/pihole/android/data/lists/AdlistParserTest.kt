package org.pihole.android.data.lists

import org.junit.Assert.assertTrue
import org.junit.Test

class AdlistParserTest {

    @Test
    fun parseHostsStyle_normalizesFqdn() {
        val text = "0.0.0.0 bad.example.com\n"
        val set = AdlistParser.parseHostsStyle(text)
        assertTrue(set.contains("bad.example.com."))
    }
}
