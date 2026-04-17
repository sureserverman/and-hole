package org.pihole.android.data.lists

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.pihole.android.data.db.DatabaseProvider

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = DatabaseProvider.get(applicationContext)
            AdlistRefreshEngine.refreshAll(applicationContext, db)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
