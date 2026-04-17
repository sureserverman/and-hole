package org.pihole.android.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import org.pihole.android.service.DnsForegroundService

/**
 * Debug-only: starts [DnsForegroundService] from a visible activity so FGS policy allows it
 * (broadcast alone is denied on API 34+). Invoked by [scripts/verify-harness.sh] --dns-probe.
 */
class DebugStartDnsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(
            this,
            Intent(this, DnsForegroundService::class.java),
        )
        finish()
    }
}
