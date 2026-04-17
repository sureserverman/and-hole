package org.pihole.android.feature.home.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pihole.android.data.prefs.AppPreferences
import org.pihole.android.data.runtime.DnsControlRepository

class SetupViewModelFactory(
    private val repository: DnsControlRepository,
    private val prefs: AppPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
            return SetupViewModel(repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown SetupViewModel class: ${modelClass.name}")
    }
}
