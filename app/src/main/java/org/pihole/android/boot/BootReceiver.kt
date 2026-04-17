package org.pihole.android.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.service.DnsForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val autoStart = runBlocking {
            AppPreferences(context.applicationContext).autoStartEnabled.first()
        }
        if (!autoStart) return
        val start = Intent(context, DnsForegroundService::class.java)
        context.startForegroundService(start)
    }
}
