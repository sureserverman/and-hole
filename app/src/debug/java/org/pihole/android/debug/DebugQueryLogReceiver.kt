package org.pihole.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.pihole.android.data.db.DatabaseProvider

/**
 * Debug-only adb hook to check query-log growth without requiring sqlite3 on-device.
 */
class DebugQueryLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STATS) return

        val pending = goAsync()
        runBlocking {
            try {
                val db = DatabaseProvider.get(context.applicationContext)
                val count = db.queryLogDao().observeCount().first()
                val msg = "query_log count=$count"
                Log.i(TAG, msg)
            } catch (e: Exception) {
                Log.e(TAG, "query_log stats failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG: String = "DebugQueryLog"
        const val ACTION_STATS: String = "org.pihole.android.debug.QUERY_LOG_STATS"
    }
}

