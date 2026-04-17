package org.pihole.android.data.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first

class LogPruneWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val days = AppPreferences(applicationContext).logRetentionDaysFlow.first()
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        DatabaseProvider.get(applicationContext).queryLogDao().deleteOlderThan(cutoff)
        return Result.success()
    }
}
