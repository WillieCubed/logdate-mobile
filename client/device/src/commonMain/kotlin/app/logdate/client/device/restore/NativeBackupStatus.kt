package app.logdate.client.device.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class NativeBackupQuotaFailure(
    val backupBytes: Long,
    val quotaBytes: Long,
)

sealed interface NativeBackupStatus {
    data object Unsupported : NativeBackupStatus

    data class Available(
        val lastQuotaFailure: NativeBackupQuotaFailure? = null,
    ) : NativeBackupStatus
}

interface NativeBackupStatusSource {
    fun observe(): Flow<NativeBackupStatus>
}

object UnsupportedNativeBackupStatusSource : NativeBackupStatusSource {
    override fun observe(): Flow<NativeBackupStatus> = flowOf(NativeBackupStatus.Unsupported)
}
