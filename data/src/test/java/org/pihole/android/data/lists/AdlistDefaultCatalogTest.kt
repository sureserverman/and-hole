package org.pihole.android.data.lists

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AdlistDefaultCatalogTest {

    @Test
    fun parseJson_dedupesAndSkipsNonHttp() {
        val json =
            """
            [
              { "url": "https://a.example/list", "enabled": true },
              { "address": "https://a.example/list", "enabled": false },
              { "url": "ftp://ignore.me" },
              { "url": "http://b.example/", "enabled": false }
            ]
            """.trimIndent()
        val entries = AdlistDefaultCatalog.parseJson(json)
        assertThat(entries.map { it.url to it.enabled }).containsExactly(
            "https://a.example/list" to true,
            "http://b.example/" to false,
        )
    }
}
