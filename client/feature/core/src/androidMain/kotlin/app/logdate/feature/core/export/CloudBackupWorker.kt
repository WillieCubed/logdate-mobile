package app.logdate.feature.core.export

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.archive.ArchiveExportOptions
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.CloudBackupDataSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import java.io.File
import java.io.FileOutputStream
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
        val session = sessionStorage.getSession()
        if (session == null) {
            Napier.d("CloudBackupWorker: no authenticated session; skipping")
            return Result.success()
        }

        val recoveryVerified =
            runCatching { identityKeyManager.isRecoveryPhraseVerified() }
                .getOrElse { error ->
                    Napier.w("CloudBackupWorker: could not check recovery setup", error)
                    return Result.retry()
                }
        if (!recoveryVerified) {
            Napier.d("CloudBackupWorker: recovery setup is incomplete; skipping backup")
            return Result.success()
        }

        val archive = File(context.filesDir, "cloud-backup-$id.zip")
        return try {
            val export =
                FileOutputStream(archive).use { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        exportArchiveUseCase
                            .export(ArchiveExportOptions(), ZipStreamArchiveContainer(zip))
                            .firstOrNull { it is ArchiveExportProgress.Completed || it is ArchiveExportProgress.Failed }
                    }
                }
            if (export !is ArchiveExportProgress.Completed) {
                archive.delete()
                return Result.retry()
            }
            val hasUnreadableData =
                export.summary.scope.omitted
                    .any { omission -> omission.reason == ArchiveOmissionReason.UNREADABLE }
            if (hasUnreadableData) {
                Napier.w("CloudBackupWorker: archive omitted unreadable data; backup was not uploaded")
                return Result.failure()
            }
            ZipFile(archive).use { zip ->
                requireNotNull(zip.getEntry(MANIFEST_PATH)) { "V2 archive is missing $MANIFEST_PATH" }
            }
            val encryptedArchive = cloudArchiveCipher.encrypt(archive)
            val uploadResult =
                cloudBackupDataSource.uploadBackup(
                    accessToken = session.accessToken,
                    backup =
                        BackupFile(
                            deviceId = deviceIdProvider.getDeviceId().value.toString(),
                            manifest = CloudArchiveCipher.MANIFEST,
                            data = encryptedArchive,
                        ),
                )

            uploadResult.fold(
                onSuccess = {
                    Result.success()
                },
                onFailure = { error ->
                    Napier.w("CloudBackupWorker: upload failed; WorkManager will retry", error)
                    Result.retry()
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Napier.w("CloudBackupWorker: backup failed; WorkManager will retry", error)
            Result.retry()
        } finally {
            archive.delete()
        }
    }

    companion object {
        const val WORK_NAME = "logdate:cloud:backup"
        private const val MANIFEST_PATH = "manifest.json"
        val NETWORK_CONSTRAINTS =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
