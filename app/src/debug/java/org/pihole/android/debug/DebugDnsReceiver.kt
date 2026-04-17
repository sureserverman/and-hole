package org.pihole.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import org.pihole.android.MainActivity
import org.pihole.android.service.DnsForegroundService

/**
 * Debug-only adb hooks (merged manifest `android:exported="true"`). Not in release.
 *
 * Prefer [DebugStartDnsActivity] for starting DNS on API 34+; [START_DNS] here chains into that
 * activity so `am broadcast` alone still works from host scripts.
 */
class DebugDnsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DebugActions.STOP_DNS -> {
                context.stopService(Intent(context, DnsForegroundService::class.java))
            }
            DebugActions.START_DNS -> {
                context.startActivity(
                    Intent(context, DebugStartDnsActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK),
                )
            }
            DebugActions.OPEN_MAIN -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    companion object {
        /** Same as [DebugActions.STOP_DNS]; kept for older scripts referencing the constant. */
        const val ACTION_STOP: String = DebugActions.STOP_DNS
    }
}
