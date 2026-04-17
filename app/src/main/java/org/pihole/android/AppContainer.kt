package org.pihole.android

import android.content.Context
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.DatabaseProvider
import org.pihole.android.data.prefs.AppPreferences

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { DatabaseProvider.get(appContext) }
    val preferences: AppPreferences by lazy { AppPreferences(appContext) }
}
