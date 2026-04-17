package org.pihole.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.lists.AdlistDefaultCatalog
import org.pihole.android.data.lists.AdlistRefreshScheduler
import org.pihole.android.data.runtime.UpstreamResolverDefaults

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        AdlistRefreshScheduler.schedulePeriodic(this)
        appScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.get(this@App)
            AdlistDefaultCatalog.mergeDefaultAdlistsFromAssets(this@App, db)
            UpstreamResolverDefaults.seedIfEmpty(db)
        }
    }
}
