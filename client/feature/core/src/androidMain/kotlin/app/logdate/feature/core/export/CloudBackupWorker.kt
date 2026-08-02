package app.logdate.feature.core.export

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.CloudBackupDataSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Builds a private export archive and uploads it to LogDate Cloud when authenticated. */
class CloudBackupWorker(
    private val context: Context,
    params: WorkerParameters,
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val cloudBackupDataSource: CloudBackupDataSource,
    private val sessionStorage: SessionStorage,
    private val deviceIdProvider: DeviceIdProvider,
) : CoroutineWorker(context, params),
    KoinComponent {
    override suspend fun doWork(): Result {
        val session = sessionStorage.getSession()
        if (session == null) {
            Napier.d("CloudBackupWorker: no authenticated session; skipping")
            return Result.success()
        }

        val archive = File(context.filesDir, "cloud-backup-${id}.zip")
        return try {
            val export =
                exportUserDataUseCase
                    .exportUserData()
                    .firstOrNull { it is ExportProgress.Completed }
                    ?.let { (it as ExportProgress.Completed).result }
                    ?: return Result.retry()

            AndroidExportArchiveWriter(context).writeToAppPrivateFile(export, archive.name)
            val uploadResult =
                cloudBackupDataSource.uploadBackup(
                    accessToken = session.accessToken,
                    backup =
                        BackupFile(
                            deviceId = deviceIdProvider.getDeviceId().value.toString(),
                            manifest = export.serializeMetadata(),
                            data = archive.readBytes(),
                        ),
                )

            uploadResult.fold(
                onSuccess = {
                    archive.delete()
                    Result.success()
                },
                onFailure = { error ->
                    Napier.w("CloudBackupWorker: upload failed; retaining archive for retry", error)
                    Result.retry()
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Napier.w("CloudBackupWorker: backup failed; retaining archive for retry", error)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "logdate:cloud:backup"
        val NETWORK_CONSTRAINTS =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
