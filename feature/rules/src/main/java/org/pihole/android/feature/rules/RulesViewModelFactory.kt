package org.pihole.android.feature.rules

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pihole.android.data.db.AppDatabase

class RulesViewModelFactory(
    private val application: Application,
    private val db: AppDatabase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RulesViewModel::class.java)) {
            return RulesViewModel(application, db) as T
        }
        error("Unknown ViewModel class")
    }
}
