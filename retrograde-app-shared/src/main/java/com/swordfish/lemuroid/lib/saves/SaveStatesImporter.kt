package com.swordfish.lemuroid.lib.saves

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SaveStatesImporter(private val directoriesManager: DirectoriesManager) {

    suspend fun importSrm(
        context: Context,
        game: Game,
        uri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = File(
                directoriesManager.getSavesDirectory(),
                "${game.fileName.substringBeforeLast(".")}.srm",
            )
            destFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)!!.use { it.copyTo(destFile.outputStream()) }
            Unit
        }
    }

    /**
     * @param slot 0 = auto-save, 1–4 = save slots
     */
    suspend fun importState(
        context: Context,
        game: Game,
        coreID: CoreID,
        stateUri: Uri,
        metadataUri: Uri?,
        slot: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val statesDir = directoriesManager.getStatesDirectory()
            val suffix = if (slot == 0) "state" else "slot$slot"
            val stateFile = File(statesDir, "${coreID.coreName}/${game.fileName}.$suffix")
            val metaFile = File(statesDir, "${coreID.coreName}/${game.fileName}.$suffix.metadata")

            stateFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(stateUri)!!.use { it.copyTo(stateFile.outputStream()) }

            if (metadataUri != null) {
                context.contentResolver.openInputStream(metadataUri)!!.use { it.copyTo(metaFile.outputStream()) }
            } else {
                metaFile.delete()
            }
            Unit
        }
    }
}
