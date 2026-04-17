package org.pihole.android.testutil

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.pihole.android.service.DnsForegroundService

/**
 * Stops [DnsForegroundService], drains the main thread so [android.app.Service.onDestroy] can run
 * before the next [android.content.Context.startForegroundService] (avoids FGS timeout races), then
 * sleeps briefly for socket release.
 */
object DnsServiceTestPrep {
    fun stopAndSettle(context: Context) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val stop = Intent(context, DnsForegroundService::class.java)
        // Run stop + idle wait on the main thread so it serializes ahead of the next FGS start.
        inst.runOnMainSync {
            context.stopService(stop)
        }
        runCatching { inst.waitForIdleSync() }
        Thread.sleep(4_000L)
    }
}
