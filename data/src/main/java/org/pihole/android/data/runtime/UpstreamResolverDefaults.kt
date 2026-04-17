package org.pihole.android.data.runtime

import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.UpstreamResolverEntity

object UpstreamResolverDefaults {
    private val defaults: List<UpstreamResolverEntity> =
        listOf(
            UpstreamResolverEntity(
                label = "Cloudflare Tor Onion",
                host = "dns4torpnlfs2ifuz2s2yf3fc7rdmsbhm6rw75euj35pac6ap25zgqad.onion",
                port = 853,
                tlsServerName = null,
                enabled = true,
                sortOrder = 0,
            ),
            UpstreamResolverEntity(
                label = "Cloudflare Tor Hostname",
                host = "tor.cloudflare-dns.com",
                port = 853,
                tlsServerName = null,
                enabled = true,
                sortOrder = 1,
            ),
        )

    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.upstreamResolverDao().getAllOrdered().isNotEmpty()) return
        defaults.forEach { db.upstreamResolverDao().insert(it) }
    }
}

