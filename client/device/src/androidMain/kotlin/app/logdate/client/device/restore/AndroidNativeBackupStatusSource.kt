package app.logdate.client.device.restore

import android.content.Context
import android.os.FileObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class AndroidNativeBackupStatusSource(
    context: Context,
    private val retryRecoveryAction: () -> Unit = {},
    stagedMediaInventories: () -> List<NativeJournalMediaInventory> = { emptyList() },
    isRecoverableStagedAuxiliary: (NativeIncomingJournal.Payload) -> Boolean = { false },
) : NativeBackupStatusSource {
    private val appContext = context
    private val directory = context.noBackupFilesDir
    private val custodyDirectory = File(directory, "native-journal-replay-custody")
    private val recoveryDirectory = File(context.filesDir, "db_recovery")
    private val store = NativeBackupQuotaStore(directory)
    private val custodyStatus = NativeJournalCustodyStatusReader(context, stagedMediaInventories, isRecoverableStagedAuxiliary)

    override fun retryRecovery() = retryRecoveryAction()

    @Suppress("DEPRECATION")
    override fun observe(): Flow<NativeBackupStatus> =
        callbackFlow {
            directory.mkdirs()
            custodyDirectory.mkdirs()
            recoveryDirectory.mkdirs()

            fun status() =
                NativeBackupStatus.Available(
                    store.read(),
                    custodyStatus.read(),
                    runCatching {
                        requireRetainedJournalBackupReady(appContext, deviceTransfer = false)
                        true
                    }.getOrDefault(false),
                )
            val observer =
                object : FileObserver(
                    directory.absolutePath,
                    FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.DELETE,
                ) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        if (path == NativeBackupQuotaStore.FILENAME || path == custodyDirectory.name) trySend(status())
                    }
                }
            val custodyObserver =
                object : FileObserver(
                    custodyDirectory.absolutePath,
                    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO,
                ) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        trySend(status())
                    }
                }
            val workspaceObserver =
                object : FileObserver(
                    recoveryDirectory.absolutePath,
                    FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.DELETE,
                ) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        if (path == "retained_workspace.json") trySend(status())
                    }
                }
            observer.startWatching()
            custodyObserver.startWatching()
            workspaceObserver.startWatching()
            trySend(status())
            awaitClose {
                observer.stopWatching()
                custodyObserver.stopWatching()
                workspaceObserver.stopWatching()
            }
        }.flowOn(Dispatchers.IO)
}
