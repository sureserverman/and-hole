package org.pihole.android.feature.lists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.lists.AdlistRefreshEngine
import org.pihole.android.data.lists.storage.AdlistDomainCacheStore

class ListsViewModel(
    application: Application,
    private val db: AppDatabase,
) : AndroidViewModel(application) {

    val sources =
        db.adlistSourceDao().observeAll().stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList(),
        )

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _urlError = MutableStateFlow<String?>(null)
    val urlError: StateFlow<String?> = _urlError.asStateFlow()

    fun clearUrlError() {
        _urlError.value = null
    }

    /**
     * Validates [rawUrl], inserts on a background dispatcher, then invokes [onInserted] on the main
     * thread after persistence succeeds (so the dialog can close only once the row is in Room).
     */
    fun addSource(rawUrl: String, onInserted: () -> Unit) {
        val url = rawUrl.trim()
        if (!isValidAdlistUrl(url)) {
            _urlError.value = getApplication<Application>().getString(R.string.lists_invalid_url)
            return
        }
        _urlError.value = null
        viewModelScope.launch {
            val err =
                runCatching {
                    withContext(Dispatchers.IO) {
                        db.adlistSourceDao().insert(
                            AdlistSourceEntity(
                                url = url,
                                enabled = true,
                                etag = null,
                                lastModified = null,
                                lastRefreshStartedAt = null,
                                lastSuccessAt = null,
                                lastResult = null,
                                lastError = null,
                            ),
                        )
                    }
                }.exceptionOrNull()
            if (err != null) {
                _urlError.value = err.message ?: getApplication<Application>().getString(R.string.lists_invalid_url)
            } else {
                onInserted()
            }
        }
    }

    fun setEnabled(entity: AdlistSourceEntity, enabled: Boolean) {
        if (entity.enabled == enabled) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.adlistSourceDao().update(entity.copy(enabled = enabled))
            }
        }
    }

    fun deleteSource(entity: AdlistSourceEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AdlistDomainCacheStore.delete(getApplication(), entity.id)
                db.adlistSourceDao().delete(entity)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) {
                    AdlistRefreshEngine.refreshAll(getApplication(), db)
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun isValidAdlistUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }
}

class ListsViewModelFactory(
    private val application: Application,
    private val db: AppDatabase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListsViewModel::class.java)) {
            return ListsViewModel(application, db) as T
        }
        error("Unknown ViewModel class")
    }
}
