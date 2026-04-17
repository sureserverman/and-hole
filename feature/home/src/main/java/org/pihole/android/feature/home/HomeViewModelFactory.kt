package org.pihole.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pihole.android.data.runtime.DnsControlRepository

class HomeViewModelFactory(
    private val repository: DnsControlRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown HomeViewModel class: ${modelClass.name}")
    }
}
