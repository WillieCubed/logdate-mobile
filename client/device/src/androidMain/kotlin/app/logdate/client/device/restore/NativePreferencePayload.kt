package app.logdate.client.device.restore

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

internal fun readNativePreferencePayload(
    data: ParcelFileDescriptor,
    size: Long,
): ByteArray {
    if (size !in 0..1_048_576) throw IOException("Native preference payload has an invalid size")
    val bytes = ByteArray(size.toInt())
    ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { input ->
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw IOException("Native preference delivery ended before its declared size")
            offset += count
        }
    }
    return bytes
}

internal fun restoreNativePreferencePayload(
    context: Context,
    bytes: ByteArray,
    restore: (ParcelFileDescriptor) -> Unit,
) {
    // The snapshot lock serializes this bounded scratch file; a crash leaves at most one copy.
    val staged = File(context.noBackupFilesDir, "native-widget-preferences.input")
    try {
        staged.writeBytes(bytes)
        ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use(restore)
    } finally {
        staged.delete()
    }
}
