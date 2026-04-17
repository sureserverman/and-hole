package org.pihole.android.feature.settings

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.pihole.android.data.backup.DataBackup
import org.pihole.android.data.db.AppDatabase

sealed interface SettingsBackupEvent {
    data class ShareUri(val uri: Uri) : SettingsBackupEvent
    data class Message(val text: String) : SettingsBackupEvent
}

class SettingsBackupViewModel(
    app: Application,
    private val db: AppDatabase,
) : AndroidViewModel(app) {
    private val _events = MutableSharedFlow<SettingsBackupEvent>(extraBufferCapacity = 4)
    val backupEvents = _events.asSharedFlow()

    fun exportBackupJson() =
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    val json = DataBackup.exportJson(db)
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    val file = File(dir, "pihole-backup.json")
                    file.writeText(json)
                    FileProvider.getUriForFile(
                        getApplication(),
                        "${getApplication<Application>().packageName}.fileprovider",
                        file,
                    )
                }
            result.fold(
                onSuccess = { uri -> _events.emit(SettingsBackupEvent.ShareUri(uri)) },
                onFailure = { e -> _events.emit(SettingsBackupEvent.Message("Export failed: ${e.message}")) },
            )
        }

    fun importBackupJson(uri: Uri) =
        viewModelScope.launch(Dispatchers.IO) {
            val text =
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { inp ->
                        inp.bufferedReader().readText()
                    }
                }.getOrNull()
            if (text.isNullOrBlank()) {
                _events.emit(SettingsBackupEvent.Message("Could not read backup file"))
                return@launch
            }
            val result = DataBackup.importJson(db, text)
            result.fold(
                onSuccess = {
                    _events.emit(
                        SettingsBackupEvent.Message(
                            "Import OK. Run Lists → Refresh to rebuild the blocklist snapshot.",
                        ),
                    )
                },
                onFailure = { e ->
                    _events.emit(SettingsBackupEvent.Message(e.message ?: "Import failed"))
                },
            )
        }
}

class SettingsBackupViewModelFactory(
    private val app: Application,
    private val db: AppDatabase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsBackupViewModel::class.java)) {
            return SettingsBackupViewModel(app, db) as T
        }
        error("Unknown ViewModel class")
    }
}
