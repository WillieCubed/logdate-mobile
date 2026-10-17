package app.logdate.client.device.restore

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** Durable native delivery state; content and account completion are acknowledged independently. */
class PostRestoreDetector(
    private val context: Context,
) {
    fun recordRestoreCompleted() =
        synchronized(lock) {
            val id = Uuid.random().toString()
            writeAtomically(mediaQuarantineFile(), id)
            writeAtomically(pendingRestoreFile(), "delivery-pending:$id")
            writeAtomically(accountRecoveryFile(), id)
            deleteIfPresent(accountResolvedFile())
            writeAtomically(pendingRestoreFile(), "${if (hasDatabase()) CONTENT_PRESENT else CONTENT_MISSING}:$id")
        }

    fun pendingMediaQuarantineId(): String? =
        synchronized(lock) {
            val file = mediaQuarantineFile()
            if (!file.exists()) null else readIdentifier(file) ?: Uuid.random().toString().also { writeAtomically(file, it) }
        }

    fun acknowledgeMediaQuarantine(id: String): Boolean =
        synchronized(lock) {
            if (readIdentifier(mediaQuarantineFile()) != id) return@synchronized false
            deleteIfPresent(mediaQuarantineFile())
            true
        }

    fun pendingContentRecoveryId(): String? =
        synchronized(lock) {
            val record = readDelivery() ?: return@synchronized null
            if (record.first != CONTENT_MISSING) return@synchronized null
            record.second ?: Uuid.random().toString().also { writeAtomically(pendingRestoreFile(), "$CONTENT_MISSING:$it") }
        }

    fun isContentRecoveryCurrent(deliveryId: String): Boolean =
        synchronized(lock) {
            readDelivery() == (CONTENT_MISSING to deliveryId)
        }

    fun acknowledgeContentRecovery(deliveryId: String): Boolean =
        synchronized(lock) {
            if (!isContentRecoveryCurrent(deliveryId)) return@synchronized false
            writeAtomically(pendingRestoreFile(), "content-complete:$deliveryId")
            markDeviceInitialized()
            true
        }

    fun isAccountRecoveryPending(): Boolean =
        synchronized(lock) {
            accountRecoveryFile().exists() || (!initializedFile().exists() && !accountResolvedFile().exists())
        }

    fun pendingAccountRecoveryId(): String? =
        synchronized(lock) {
            if (!isAccountRecoveryPending()) return@synchronized null
            val file = accountRecoveryFile()
            readIdentifier(file) ?: Uuid.random().toString().also { writeAtomically(file, it) }
        }

    fun markAccountRecoveryPending() =
        synchronized(lock) {
            if (!accountRecoveryFile().exists()) writeAtomically(accountRecoveryFile(), Uuid.random().toString())
            deleteIfPresent(accountResolvedFile())
        }

    fun acknowledgeAccountRecovery(expectedId: String? = pendingAccountRecoveryId()) =
        synchronized(lock) {
            if (expectedId != null && readIdentifier(accountRecoveryFile()) != expectedId) return@synchronized
            writeAtomically(accountResolvedFile(), expectedId ?: "resolved")
            deleteIfPresent(accountRecoveryFile())
        }

    fun detect(isOnboarded: Boolean): PostRestoreType =
        synchronized(lock) {
            val pending = pendingRestoreFile()
            if (initializedFile().exists() && !pending.exists()) return@synchronized PostRestoreType.NONE
            if (!isOnboarded && !pending.exists()) return@synchronized PostRestoreType.NONE
            when (readDelivery()?.first) {
                CONTENT_PRESENT, "content-complete" -> PostRestoreType.CONTENT_RESTORED
                CONTENT_MISSING -> PostRestoreType.CONTENT_MISSING
                else ->
                    when {
                        hasDatabase() -> PostRestoreType.CONTENT_RESTORED
                        pending.exists() -> PostRestoreType.RECOVERY_REQUIRED
                        else -> PostRestoreType.CONTENT_MISSING
                    }
            }
        }

    fun markDeviceInitialized() =
        synchronized(lock) {
            writeAtomically(initializedFile(), "initialized")
            deleteIfPresent(pendingRestoreFile())
        }

    private fun readDelivery(): Pair<String, String?>? {
        val text = readSmallFile(pendingRestoreFile()) ?: return null
        if (text == CONTENT_PRESENT || text == CONTENT_MISSING) return text to null
        val parts = text.split(':', limit = 2)
        if (parts.size != 2 || runCatching { Uuid.parse(parts[1]) }.isFailure) return null
        return parts[0] to parts[1]
    }

    private fun readIdentifier(file: File): String? = readSmallFile(file)?.takeIf { runCatching { Uuid.parse(it) }.isSuccess }

    private fun readSmallFile(file: File): String? =
        runCatching {
            if (file.length() in 1..128) file.readText() else null
        }.getOrNull()

    private fun writeAtomically(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use {
            it.write(text.encodeToByteArray())
            it.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun deleteIfPresent(file: File) {
        check(!file.exists() || file.delete()) { "Native recovery acknowledgement could not be persisted" }
    }

    private fun hasDatabase(): Boolean = context.getDatabasePath("logdate").let { it.exists() && it.length() > 0L }

    private fun initializedFile() = File(context.noBackupFilesDir, ".logdate_device_initialized")

    private fun pendingRestoreFile() = File(context.noBackupFilesDir, ".logdate_native_restore_pending")

    private fun mediaQuarantineFile() = File(context.noBackupFilesDir, ".logdate_media_quarantine_pending")

    private fun accountRecoveryFile() = File(context.noBackupFilesDir, ".logdate_account_restore_pending")

    private fun accountResolvedFile() = File(context.noBackupFilesDir, ".logdate_account_restore_resolved")

    private companion object {
        val lock = Any()
        const val CONTENT_PRESENT = "content-present"
        const val CONTENT_MISSING = "content-missing"
    }
}

enum class PostRestoreType {
    RECOVERY_REQUIRED,
    CONTENT_RESTORED,
    CONTENT_MISSING,
    NONE,
}
