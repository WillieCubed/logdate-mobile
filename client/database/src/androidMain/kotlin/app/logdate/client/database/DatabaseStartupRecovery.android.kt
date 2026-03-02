package app.logdate.client.database

import android.content.Context
import android.util.Log
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface DatabaseStartupState {
    data object Ready : DatabaseStartupState

    data class RecoveryRequired(
        val reason: String,
        val detail: String,
    ) : DatabaseStartupState
}

class DatabaseStartupMonitor {
    private val _state = MutableStateFlow<DatabaseStartupState>(DatabaseStartupState.Ready)
    val state: StateFlow<DatabaseStartupState> = _state.asStateFlow()

    fun markReady() {
        _state.value = DatabaseStartupState.Ready
        Log.i(DB_RECOVERY_TAG, "Database startup state: Ready")
    }

    fun markRecoveryRequired(error: Throwable) {
        val rootCause = error.rootCause()
        val reason = classifyRecoveryReason(rootCause.message.orEmpty())
        val detail = "${rootCause::class.java.simpleName}: ${rootCause.message ?: "unknown"}"
        _state.value = DatabaseStartupState.RecoveryRequired(reason = reason, detail = detail)
        Log.e(DB_RECOVERY_TAG, "Database startup state: RecoveryRequired ($reason)")
    }
}

class DatabaseRecoveryController(
    private val context: Context,
    private val passphraseProvider: DatabasePassphraseProvider,
    private val startupMonitor: DatabaseStartupMonitor,
) {
    suspend fun quarantineAndResetEncryptedStorage(): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val dbFile = appContext.getDatabasePath(DATABASE_NAME)
                val backupBase =
                    File(
                        dbFile.parentFile,
                        "$DATABASE_NAME.recovery-backup-${System.currentTimeMillis()}",
                    )

                Log.w(
                    DB_RECOVERY_TAG,
                    "Reset requested. Quarantining current encrypted DB before clearing key.",
                )
                moveIfExists(dbFile, backupBase)
                moveIfExists(File("${dbFile.absolutePath}-wal"), File("${backupBase.absolutePath}-wal"))
                moveIfExists(File("${dbFile.absolutePath}-shm"), File("${backupBase.absolutePath}-shm"))
                moveIfExists(File("${dbFile.absolutePath}-journal"), File("${backupBase.absolutePath}-journal"))

                passphraseProvider.clearPassphrase()
                startupMonitor.markReady()

                Log.w(
                    DB_RECOVERY_TAG,
                    "Encrypted DB/key reset complete. Backup preserved at ${backupBase.absolutePath}",
                )

                backupBase
            }
        }
}

private fun classifyRecoveryReason(message: String): String {
    val normalized = message.lowercase()
    return when {
        "file is not a database" in normalized -> "Encrypted database could not be decrypted with the current key."
        "hmac check failed" in normalized -> "Encrypted database integrity check failed for the active key."
        "decrypting page" in normalized -> "Encrypted database pages could not be decrypted."
        "sqlcipher" in normalized -> "SQLCipher failed to open the encrypted database."
        else -> "Encrypted database failed to open safely."
    }
}

private fun Throwable.rootCause(): Throwable {
    var current = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}

private fun moveIfExists(
    source: File,
    target: File,
) {
    if (!source.exists()) {
        return
    }
    moveReplacing(source, target)
}

private fun moveReplacing(
    source: File,
    target: File,
) {
    target.parentFile?.mkdirs()
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

private const val DB_RECOVERY_TAG = "LogDateDbRecovery"
