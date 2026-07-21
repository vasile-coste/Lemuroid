package com.swordfish.lemuroid.app.mobile.feature.gamemenu.saves

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun GameMenuSavesScreen(
    viewModel: GameMenuSavesViewModel,
    game: Game,
) {
    val context = LocalContext.current
    val transferState by viewModel.transferState.collectAsState()
    val pendingStateImport by viewModel.pendingStateImport.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { viewModel.exportSaves(it) }
        }
    }

    val srmImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { viewModel.importSrm(it) }
        }
    }

    val stateImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult

        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } ?: data.data?.let { uris.add(it) }

        fun getDisplayName(uri: Uri): String? =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }

        var stateUri: Uri? = null
        var metadataUri: Uri? = null
        for (uri in uris) {
            val name = getDisplayName(uri) ?: continue
            if (name.endsWith(".metadata")) metadataUri = uri else stateUri = uri
        }
        stateUri?.let { viewModel.onStateFilesSelected(it, metadataUri) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LemuroidSettingsMenuLink(
            title = { Text(stringResource(R.string.game_menu_saves_export)) },
            onClick = {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "${game.title} saves.zip")
                }
                exportLauncher.launch(intent)
            },
        )

        LemuroidSettingsMenuLink(
            title = { Text(stringResource(R.string.game_menu_saves_import_srm)) },
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                srmImportLauncher.launch(intent)
            },
        )

        LemuroidSettingsMenuLink(
            title = { Text(stringResource(R.string.game_menu_saves_import_state)) },
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                stateImportLauncher.launch(intent)
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val state = transferState) {
                is GameMenuSavesViewModel.TransferState.Idle -> {}
                is GameMenuSavesViewModel.TransferState.Working -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.game_menu_saves_working),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is GameMenuSavesViewModel.TransferState.Success -> {
                    Text(
                        text = stringResource(R.string.game_menu_saves_success),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is GameMenuSavesViewModel.TransferState.Error -> {
                    Text(
                        text = stringResource(R.string.game_menu_saves_error, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (pendingStateImport != null) {
        SlotSelectionDialog(
            onSlotSelected = { viewModel.importState(it) },
            onDismiss = { viewModel.dismissSlotDialog() },
        )
    }
}

@Composable
private fun SlotSelectionDialog(
    onSlotSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_menu_saves_slot_select)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSlotSelected(0) }) {
                    Text(stringResource(R.string.game_menu_saves_slot_auto))
                }
                for (slot in 1..4) {
                    TextButton(onClick = { onSlotSelected(slot) }) {
                        Text(stringResource(R.string.game_menu_saves_slot_n, slot))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
