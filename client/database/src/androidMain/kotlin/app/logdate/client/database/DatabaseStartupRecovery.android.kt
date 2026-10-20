package app.logdate.client.database

import android.content.Context
import app.logdate.client.database.encryption.DatabaseKeyRecoveryRequiredException
import app.logdate.client.database.encryption.DatabasePassphraseProvider
import app.logdate.client.device.restore.NativeRestoreDeliveryGate
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface DatabaseStartupState {
    data object Initializing : DatabaseStartupState

    data object Ready : DatabaseStartupState

    data class RecoveryRequired(
        val reason: String,
        val detail: String,
    ) : DatabaseStartupState
}

class NativeRestoreInProgressException : IllegalStateException("Native restore delivery is incomplete")

internal suspend fun <T> openDatabaseAfterNativeRestoreCheck(
    deliveryGate: NativeRestoreDeliveryGate,
    restoreInProgress: () -> Boolean,
    openDatabase: () -> T,
): T =
    deliveryGate.withLock {
        if (restoreInProgress()) throw NativeRestoreInProgressException()
        openDatabase()
    }

internal data class OpenedDatabase<T>(
    val database: T,
    val passphrase: ByteArray,
)

internal fun <T> openDatabaseWithPassphraseCandidates(
    candidates: List<ByteArray>,
    openDatabase: (ByteArray) -> T,
): OpenedDatabase<T> {
    require(candidates.isNotEmpty()) { "No database passphrase candidates are available" }
    var lastError: Exception? = null
    var nonKeyError: Exception? = null
    candidates.forEach { candidate ->
        try {
            return OpenedDatabase(openDatabase(candidate), candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (newer: NewerDatabaseVersionException) {
            throw newer
        } catch (error: Exception) {
            lastError = error
            val message =
                error
                    .rootCause()
                    .message
                    .orEmpty()
                    .lowercase()
            if (listOf("file is not a database", "hmac check failed", "decrypting page").none { it in message }) {
                nonKeyError = error
            }
        }
    }
    throw nonKeyError ?: DatabaseKeyRecoveryRequiredException(lastError)
}

class DatabaseStartupMonitor {
    private val _state = MutableStateFlow<DatabaseStartupState>(DatabaseStartupState.Initializing)
    val state: StateFlow<DatabaseStartupState> = _state.asStateFlow()

    fun markReady() {
        _state.value = DatabaseStartupState.Ready
        Napier.i("Database startup state: Ready", tag = DB_RECOVERY_TAG)
    }

    fun markRecoveryRequired(error: Throwable) {
        val rootCause = error.rootCause()
        val reason =
            if (rootCause is NewerDatabaseVersionException) {
                "Update LogDate to open this journal. Your journal has been kept safe."
            } else {
                classifyRecoveryReason(rootCause.message.orEmpty())
            }
        val detail = "${rootCause::class.java.simpleName}: ${rootCause.message ?: "unknown"}"
        _state.value = DatabaseStartupState.RecoveryRequired(reason = reason, detail = detail)
        Napier.e("Database startup state: RecoveryRequired ($reason)", tag = DB_RECOVERY_TAG)
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

                Napier.w("Reset requested. Quarantining current encrypted DB before clearing key.", tag = DB_RECOVERY_TAG)
                moveIfExists(dbFile, backupBase)
                moveIfExists(File("${dbFile.absolutePath}-wal"), File("${backupBase.absolutePath}-wal"))
                moveIfExists(File("${dbFile.absolutePath}-shm"), File("${backupBase.absolutePath}-shm"))
                moveIfExists(File("${dbFile.absolutePath}-journal"), File("${backupBase.absolutePath}-journal"))

                passphraseProvider.clearPassphrase()
                startupMonitor.markReady()

                Napier.w("Encrypted DB/key reset complete. Backup preserved at ${backupBase.absolutePath}", tag = DB_RECOVERY_TAG)

                backupBase
            }
        }
}

private fun classifyRecoveryReason(message: String): String {
    val normalized = message.lowercase()
    return when {
        "journal privacy settings have not been restored" in normalized -> "Your journal’s privacy settings need to finish restoring."
        "native restore delivery is incomplete" in normalized -> "Android device transfer is still finishing."
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
