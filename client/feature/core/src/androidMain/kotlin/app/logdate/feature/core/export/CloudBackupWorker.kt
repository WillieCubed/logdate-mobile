package app.logdate.feature.core.export

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.sync.cloud.BackupUploadFileRequest
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.backup.CloudBackupTokenRefresher
import app.logdate.feature.core.backup.cloudArchiveFile
import app.logdate.feature.core.backup.pruneAbandonedCloudArchives
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.io.files.Path
import org.koin.core.component.KoinComponent
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/** Builds a private export archive and uploads it to LogDate Cloud when authenticated. */
class CloudBackupWorker(
    private val context: Context,
    params: WorkerParameters,
    private val exportArchiveUseCase: ExportArchiveUseCase,
    private val cloudBackupDataSource: CloudBackupDataSource,
    private val sessionStorage: SessionStorage,
    private val deviceIdProvider: DeviceIdProvider,
    private val identityKeyManager: IdentityKeyManager,
    private val cloudArchiveCipher: CloudArchiveCipher,
) : CoroutineWorker(context, params),
    KoinComponent {
    override suspend fun doWork(): Result {
        pruneAbandonedCloudArchives(context)
        val originBoundSession = sessionStorage.getOriginBoundSession() ?: return unboundSessionResult()
        recoveryNotReadyResult()?.let { return it }
        return backUp(originBoundSession)
    }

    private fun unboundSessionResult(): Result =
        if (sessionStorage.getSession() == null) {
            Napier.d("CloudBackupWorker: no authenticated session; skipping")
            Result.success()
        } else {
            Napier.w("CloudBackupWorker: session has no server binding; retrying")
            Result.retry()
        }

    /** Returns the result to finish with when backups must wait for recovery setup, or null to continue. */
    private suspend fun recoveryNotReadyResult(): Result? {
        val recoveryVerified =
            try {
                identityKeyManager.isRecoveryPhraseVerified()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                Napier.w("CloudBackupWorker: could not check recovery setup")
                return Result.retry()
            }
        if (recoveryVerified) return null
        Napier.d("CloudBackupWorker: recovery setup is incomplete; skipping backup")
        return Result.success()
    }

    private suspend fun backUp(originBoundSession: OriginBoundSession): Result {
        val directory = context.noBackupFilesDir
        val archive = cloudArchiveFile(context, "cloud-backup", id)
        val scopeFile = File(directory, "cloud-backup-$id.scope")
        val plaintext = File(directory, "cloud-backup-$id.plain.part")
        val encryptedPart = File(directory, "cloud-backup-$id.encrypted.part")
        return try {
            if (!isReusableArchive(archive, scopeFile, originBoundSession)) {
                archive.delete()
                scopeFile.delete()
                plaintext.delete()
                encryptedPart.delete()
                buildEncryptedArchive(archive, scopeFile, plaintext, encryptedPart, originBoundSession)?.let { return it }
            }
            uploadArchive(archive, originBoundSession)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Napier.w("CloudBackupWorker: backup failed; WorkManager will retry")
            Result.retry()
        } finally {
            plaintext.delete()
            encryptedPart.delete()
        }
    }

    /** Returns the result to finish with when no archive may be uploaded, or null once [archive] is ready. */
    private suspend fun buildEncryptedArchive(
        archive: File,
        scopeFile: File,
        plaintext: File,
        encryptedPart: File,
        originBoundSession: OriginBoundSession,
    ): Result? {
        val export = exportArchive(plaintext)
        if (export !is ArchiveExportProgress.Completed) return Result.retry()
        val hasUnreadableData =
            export.summary.scope.omitted
                .any { omission -> omission.reason == ArchiveOmissionReason.UNREADABLE }
        if (hasUnreadableData) {
            Napier.w("CloudBackupWorker: archive omitted unreadable data; backup was not uploaded")
            return Result.failure()
        }
        validateArchive(plaintext)
        cloudArchiveCipher.encrypt(plaintext, encryptedPart)
        check(encryptedPart.length() > MIN_ENCRYPTED_ARCHIVE_SIZE) { "Encrypted archive is incomplete" }
        check(encryptedPart.renameTo(archive)) { "Unable to finalize encrypted archive" }
        writeScopeMarker(scopeFile, originBoundSession, archive.sha256())
        return null
    }

    private suspend fun exportArchive(archive: File): ArchiveExportProgress? =
        FileOutputStream(archive).use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                exportArchiveUseCase
                    .export(ArchiveExportOptions(), ZipStreamArchiveContainer(zip))
                    .firstOrNull { it is ArchiveExportProgress.Completed || it is ArchiveExportProgress.Failed }
            }
        }

    private suspend fun uploadArchive(
        archive: File,
        expectedSession: OriginBoundSession,
    ): Result {
        val request =
            BackupUploadFileRequest(
                deviceId = deviceIdProvider.getDeviceId().value.toString(),
                manifest = CloudArchiveCipher.MANIFEST,
                sourcePath = Path(archive.absolutePath),
                sizeBytes = archive.length(),
            )
        val upload =
            CloudBackupTokenRefresher(sessionStorage, cloudBackupDataSource)
                .execute(expectedSession) { accessToken ->
                    cloudBackupDataSource.uploadBackupFile(accessToken, request)
                }.result

        return upload.fold(
            onSuccess = {
                archive.delete()
                File(context.noBackupFilesDir, "cloud-backup-$id.scope").delete()
                Result.success()
            },
            onFailure = {
                Napier.w("CloudBackupWorker: upload failed; WorkManager will retry")
                Result.retry()
            },
        )
    }

    private fun validateArchive(archive: File) {
        ZipFile(archive).use { zip ->
            requireNotNull(zip.getEntry(MANIFEST_PATH)) { "V2 archive is missing $MANIFEST_PATH" }
        }
    }

    private fun isReusableArchive(
        archive: File,
        scopeFile: File,
        scope: OriginBoundSession,
    ): Boolean {
        if (!archive.isFile || archive.length() <= MIN_ENCRYPTED_ARCHIVE_SIZE || !scopeFile.isFile) return false
        val lines = runCatching { scopeFile.readLines(Charsets.UTF_8) }.getOrNull() ?: return false
        return lines.size == 4 &&
            lines[0] == MARKER_VERSION &&
            lines[1] == scope.origin &&
            lines[2] == scope.session.accountId &&
            lines[3] == archive.sha256()
    }

    private fun writeScopeMarker(
        marker: File,
        scope: OriginBoundSession,
        archiveDigest: String,
    ) {
        val temporary = File(marker.parentFile, "${marker.name}.part")
        temporary.writeText(
            "$MARKER_VERSION\n${scope.origin}\n${scope.session.accountId}\n$archiveDigest",
            Charsets.UTF_8,
        )
        check(temporary.renameTo(marker)) { "Unable to finalize backup retry marker" }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    companion object {
        const val WORK_NAME = "logdate:cloud:backup"
        private const val MANIFEST_PATH = "manifest.json"
        private const val MARKER_VERSION = "cloud-backup-retry-v1"
        private const val MIN_ENCRYPTED_ARCHIVE_SIZE = 33L
        private const val HASH_BUFFER_SIZE = 64 * 1024
        val NETWORK_CONSTRAINTS =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
