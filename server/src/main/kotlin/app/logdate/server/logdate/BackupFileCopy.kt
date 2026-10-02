package app.logdate.server.logdate

import java.io.InputStream
import java.io.OutputStream

internal fun copyBackupFile(
    source: InputStream,
    destination: OutputStream,
    checkActive: () -> Unit,
) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkActive()
        val count = source.read(buffer)
        if (count < 0) break
        destination.write(buffer, 0, count)
    }
}
