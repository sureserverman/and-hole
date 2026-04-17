package org.pihole.android.feature.diagnostics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.prefs.AppPreferences

class DiagnosticsViewModelFactory(
    private val application: Application,
    private val db: AppDatabase,
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiagnosticsViewModel::class.java)) {
            return DiagnosticsViewModel(application, db, prefs) as T
        }
        error("Unknown ViewModel class")
    }
}
