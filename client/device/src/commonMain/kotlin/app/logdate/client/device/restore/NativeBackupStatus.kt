package app.logdate.client.device.restore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class NativeBackupQuotaFailure(
    val backupBytes: Long,
    val quotaBytes: Long,
)

enum class NativeRestoreCustodyStatus {
    CLEAR,
    RECOVERING,
    NEEDS_TRANSFER,
}

sealed interface NativeBackupStatus {
    data object Unsupported : NativeBackupStatus

    data class Available(
        val lastQuotaFailure: NativeBackupQuotaFailure? = null,
        val custodyStatus: NativeRestoreCustodyStatus = NativeRestoreCustodyStatus.CLEAR,
        val cloudBackupReady: Boolean = true,
    ) : NativeBackupStatus
}

interface NativeBackupStatusSource {
    fun observe(): Flow<NativeBackupStatus>

    fun retryRecovery() = Unit
}

object UnsupportedNativeBackupStatusSource : NativeBackupStatusSource {
    override fun observe(): Flow<NativeBackupStatus> = flowOf(NativeBackupStatus.Unsupported)
}
