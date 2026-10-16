package app.logdate.client.device.restore

import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class NativeBackupQuotaStore(
    private val noBackupDirectory: File,
) {
    fun record(
        backupBytes: Long,
        quotaBytes: Long,
    ) {
        require(backupBytes >= 0 && quotaBytes > 0)
        noBackupDirectory.mkdirs()
        val file = File(noBackupDirectory, FILENAME)
        val temporary = File(noBackupDirectory, "$FILENAME.part")
        FileOutputStream(temporary).use {
            it.write("$backupBytes:$quotaBytes".encodeToByteArray())
            it.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun read(): NativeBackupQuotaFailure? {
        return try {
            val file = File(noBackupDirectory, FILENAME)
            if (!file.exists() || file.length() !in 1..64) return null
            val fields = file.readText().split(':')
            if (fields.size != 2) return null
            val bytes = fields[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val quota = fields[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            NativeBackupQuotaFailure(bytes, quota)
        } catch (_: Exception) {
            Napier.w("Could not read Android backup quota status")
            null
        }
    }

    companion object {
        const val FILENAME = ".logdate_native_backup_quota_exceeded"
    }
}
