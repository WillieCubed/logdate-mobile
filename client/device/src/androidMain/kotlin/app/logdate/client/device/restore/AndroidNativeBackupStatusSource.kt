package app.logdate.client.device.restore

import android.content.Context
import android.os.FileObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class AndroidNativeBackupStatusSource(
    context: Context,
) : NativeBackupStatusSource {
    private val directory = context.noBackupFilesDir
    private val store = NativeBackupQuotaStore(directory)

    @Suppress("DEPRECATION")
    override fun observe(): Flow<NativeBackupStatus> =
        callbackFlow {
            directory.mkdirs()
            val observer =
                object : FileObserver(directory.absolutePath, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.DELETE) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        if (path == NativeBackupQuotaStore.FILENAME) trySend(NativeBackupStatus.Available(store.read()))
                    }
                }
            observer.startWatching()
            trySend(NativeBackupStatus.Available(store.read()))
            awaitClose { observer.stopWatching() }
        }.flowOn(Dispatchers.IO)
}
