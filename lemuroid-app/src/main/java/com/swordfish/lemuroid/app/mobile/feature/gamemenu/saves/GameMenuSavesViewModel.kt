package com.swordfish.lemuroid.app.mobile.feature.gamemenu.saves

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveStatesExporter
import com.swordfish.lemuroid.lib.saves.SaveStatesImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameMenuSavesViewModel(
    application: Application,
    private val game: Game,
    private val coreID: CoreID,
    private val exporter: SaveStatesExporter,
    private val importer: SaveStatesImporter,
) : AndroidViewModel(application) {

    sealed class TransferState {
        object Idle : TransferState()
        object Working : TransferState()
        data class Success(val message: String) : TransferState()
        data class Error(val message: String) : TransferState()
    }

    data class PendingStateImport(val stateUri: Uri, val metadataUri: Uri?)

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState

    private val _pendingStateImport = MutableStateFlow<PendingStateImport?>(null)
    val pendingStateImport: StateFlow<PendingStateImport?> = _pendingStateImport

    fun exportSaves(uri: Uri) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            val result = exporter.exportToUri(getApplication(), game, coreID, uri)
            _transferState.value = result.fold(
                onSuccess = { TransferState.Success("ok") },
                onFailure = { e -> TransferState.Error(e.message ?: "Unknown error") },
            )
        }
    }

    fun importSrm(uri: Uri) {
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            val result = importer.importSrm(getApplication(), game, uri)
            _transferState.value = result.fold(
                onSuccess = { TransferState.Success("ok") },
                onFailure = { e -> TransferState.Error(e.message ?: "Unknown error") },
            )
        }
    }

    fun onStateFilesSelected(stateUri: Uri, metadataUri: Uri?) {
        _pendingStateImport.value = PendingStateImport(stateUri, metadataUri)
    }

    fun importState(slot: Int) {
        val pending = _pendingStateImport.value ?: return
        _pendingStateImport.value = null
        viewModelScope.launch {
            _transferState.value = TransferState.Working
            val result = importer.importState(getApplication(), game, coreID, pending.stateUri, pending.metadataUri, slot)
            _transferState.value = result.fold(
                onSuccess = { TransferState.Success("ok") },
                onFailure = { e -> TransferState.Error(e.message ?: "Unknown error") },
            )
        }
    }

    fun dismissSlotDialog() {
        _pendingStateImport.value = null
    }

    class Factory(
        private val application: Application,
        private val game: Game,
        private val coreID: CoreID,
        private val exporter: SaveStatesExporter,
        private val importer: SaveStatesImporter,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GameMenuSavesViewModel(application, game, coreID, exporter, importer) as T
        }
    }
}
