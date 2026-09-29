package app.logdate.feature.core.settings.ui

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.export.CloudArchiveCipher
import app.logdate.feature.core.export.CloudBackupScheduler
import app.logdate.feature.core.export.CloudBackupWorker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.coroutines.cancellation.CancellationException

/** Observes the actual archive worker and checks that its encrypted upload reached the account. */
class AndroidCloudArchiveStatusSource(
    context: Context,
    private val identityKeyManager: IdentityKeyManager,
    private val cloudBackupDataSource: CloudBackupDataSource,
    private val scheduler: CloudBackupScheduler,
) : CloudArchiveStatusSource {
    private val workManager = WorkManager.getInstance(context)

    override fun observe(session: UserSession): Flow<CloudArchiveStatus> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow("${CloudBackupWorker.WORK_NAME}:immediate"),
            workManager.getWorkInfosForUniqueWorkFlow(CloudBackupWorker.WORK_NAME),
        ) { immediate, periodic ->
            archiveWorkState(immediate, periodic)
        }.map { workState ->
            try {
                if (!identityKeyManager.isRecoveryPhraseVerified()) {
                    return@map CloudArchiveStatus(CloudArchivePhase.NEEDS_RECOVERY)
                }
                val remote = cloudBackupDataSource.listBackups(session.accessToken)
                resolveCloudArchiveStatus(
                    recoveryVerified = true,
                    workState = workState,
                    serverCompletedAt =
                        remote.getOrNull()?.let { backups ->
                            latestEncryptedArchiveAt(backups, CloudArchiveCipher.MANIFEST)
                        },
                    serverLookupFailed = remote.isFailure,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Napier.w("Could not read Cloud archive backup status", error)
                resolveCloudArchiveStatus(true, workState, null, true)
            }
        }.onStart { emit(CloudArchiveStatus(CloudArchivePhase.CHECKING)) }

    override suspend fun requestBackup() {
        scheduler.enqueueImmediateBackup()
    }
}

internal fun archiveWorkState(
    immediate: List<WorkInfo>,
    periodic: List<WorkInfo>,
): ArchiveWorkState {
    val work = immediate + periodic
    if (work.any { it.state == WorkInfo.State.RUNNING }) return ArchiveWorkState.RUNNING
    if (work.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }) return ArchiveWorkState.RETRYING
    if (immediate.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) {
        return ArchiveWorkState.QUEUED
    }
    if (work.any { it.state == WorkInfo.State.FAILED }) return ArchiveWorkState.FAILED
    if (work.any { it.state == WorkInfo.State.SUCCEEDED }) return ArchiveWorkState.SUCCEEDED
    return ArchiveWorkState.IDLE
}
