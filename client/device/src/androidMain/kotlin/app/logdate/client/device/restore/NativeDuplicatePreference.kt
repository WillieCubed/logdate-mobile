package app.logdate.client.device.restore

import android.os.ParcelFileDescriptor
import java.io.IOException

internal fun drainDuplicateNativePreference(
    data: ParcelFileDescriptor,
    size: Long,
) {
    ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { input ->
        val buffer = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Duplicate native preference delivery ended before its declared size")
            remaining -= count
        }
    }
}
