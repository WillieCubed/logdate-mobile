package app.logdate.server.routes.sync

import app.logdate.server.responses.error
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

internal data class ParsedBackupMultipartFile(
    val deviceId: String,
    val manifest: String,
    val path: Path,
    val sizeBytes: Long,
)

internal fun createPrivateBackupTempFile(
    prefix: String,
    suffix: String,
): Path {
    val directory = Path.of(System.getProperty("java.io.tmpdir"))
    val now = System.currentTimeMillis()
    val previousSweep = lastBackupTempSweep.get()
    if (now - previousSweep > BACKUP_TEMP_SWEEP_INTERVAL_MILLIS && lastBackupTempSweep.compareAndSet(previousSweep, now)) {
        runCatching { sweepAbandonedBackupTempFiles(directory, now, activeBackupTempFiles) }
    }
    val path =
        Files.createTempFile(
            directory,
            prefix,
            suffix,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        )
    activeBackupTempFiles.add(path)
    return path
}

internal fun deletePrivateBackupTempFile(path: Path) {
    try {
        Files.deleteIfExists(path)
    } finally {
        activeBackupTempFiles.remove(path)
    }
}

internal fun sweepAbandonedBackupTempFiles(
    directory: Path,
    nowMillis: Long,
    active: Set<Path>,
) {
    Files.newDirectoryStream(directory).use { entries ->
        entries.forEach { path ->
            runCatching {
                if (BACKUP_TEMP_PREFIXES.any { path.fileName.toString().startsWith(it) } &&
                    path !in active &&
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    nowMillis - Files.getLastModifiedTime(path).toMillis() > BACKUP_TEMP_MAX_AGE_MILLIS
                ) {
                    Files.deleteIfExists(path)
                }
            }
        }
    }
}

private val activeBackupTempFiles = ConcurrentHashMap.newKeySet<Path>()
private val lastBackupTempSweep = AtomicLong(0)
private val BACKUP_TEMP_PREFIXES =
    listOf(
        "logdate-backup-upload-",
        "logdate-backup-encrypted-",
        "logdate-backup-download-",
        "logdate-backup-verified-",
    )
private const val BACKUP_TEMP_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
private const val BACKUP_TEMP_SWEEP_INTERVAL_MILLIS = 6L * 60 * 60 * 1000

internal suspend fun ApplicationCall.receiveBackupMultipartFile(): ParsedBackupMultipartFile? =
    try {
        streamBackupMultipart()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (invalid: InvalidBackupMultipart) {
        respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", invalid.reason))
        null
    }
