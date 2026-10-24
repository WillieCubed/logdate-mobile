package app.logdate.client.device.restore

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.datastore.createAndroidMainDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Framework delivery must update live DataStore readers as well as its on-disk file. */
internal fun restoreMainPreferences(
    context: Context,
    data: ParcelFileDescriptor,
    size: Long,
    restore: (ParcelFileDescriptor) -> Unit,
) {
    val staged = File(context.noBackupFilesDir, "native-preferences-${Uuid.random()}.preferences_pb")
    val job = SupervisorJob()
    try {
        require(size >= 0)
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { input ->
            staged.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var remaining = size
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw IOException("Native preferences delivery ended before its declared size")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
        runBlocking(Dispatchers.IO) {
            val incoming = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { staged }
            val snapshot = incoming.data.first().toPreferences()
            createAndroidMainDataStore(context).updateData {
                // Preserve framework include/exclude checks at the original destination while
                // preventing application writers from racing the accepted native copy.
                ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use(restore)
                val destination = File(context.filesDir, "logdate.preferences_pb")
                if (!destination.isFile || !MessageDigest.isEqual(digest(staged), digest(destination))) {
                    throw IOException("Android did not accept the native preferences delivery")
                }
                snapshot
            }
        }
    } finally {
        runBlocking { job.cancelAndJoin() }
        staged.delete()
    }
}

internal fun restoreVerifiedNativeJournalPreferences(
    context: Context,
    file: File,
) {
    val job = SupervisorJob()
    try {
        runBlocking(Dispatchers.IO) {
            val verified = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            val snapshot = verified.data.first().toPreferences()
            createAndroidMainDataStore(context).updateData { snapshot }
        }
    } finally {
        runBlocking { job.cancelAndJoin() }
    }
}

private fun digest(file: File): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest()
}
