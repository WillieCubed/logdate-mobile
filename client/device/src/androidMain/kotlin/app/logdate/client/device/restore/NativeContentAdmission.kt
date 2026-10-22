package app.logdate.client.device.restore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

fun nativeRestoreDeliveryGate(): NativeRestoreDeliveryGate = AndroidNativeRestoreDeliveryGate

/** Admit a durable content root before writing bytes; retain that root during ordinary file cleanup. */
suspend fun admitNativeContent(directory: File) =
    withContext(Dispatchers.IO) {
        if (directory.isDirectory) return@withContext
        AndroidNativeRestoreDeliveryGate.withLock {
            check(directory.isDirectory || directory.mkdirs()) { "Local content storage is temporarily unavailable" }
        }
    }

/** Synchronous recording and archive entrypoints admit only the directory, never a copy or recording. */
fun admitNativeContentBlocking(directory: File) {
    if (directory.isDirectory) return
    runBlocking(Dispatchers.IO) { admitNativeContent(directory) }
}
