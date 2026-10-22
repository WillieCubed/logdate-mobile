package app.logdate.client.device.restore

import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** Called under the agent's callback lock before native files or delivery receipts can change. */
internal class NativeRestoreFormatGuard(
    private val noBackupDirectory: () -> File,
) {
    private var rejected = false

    fun requireNativeFile(nativeFormat: Boolean?) {
        if (rejected) throw unsupportedFormat()
        if (nativeFormat == false) {
            rejected = true
            recordRejection()
            throw unsupportedFormat()
        }
        if (nativeFormat == null) {
            if (isRejected()) throw unsupportedFormat()
        } else if (hasPersistedRejection()) {
            // Only an explicitly native modern delivery on a new agent can replace this boundary.
            val marker = marker()
            try {
                marker.delete()
                if (markerFiles(marker.baseFile).any { it.exists() }) throw unsupportedFormat()
            } catch (_: Exception) {
                rejected = true
                throw unsupportedFormat()
            }
        }
    }

    fun isRejected(): Boolean {
        if (hasPersistedRejection()) rejected = true
        return rejected
    }

    private fun hasPersistedRejection(): Boolean = markerFiles(marker().baseFile).any { it.exists() }

    private fun recordRejection() {
        val marker = marker()
        try {
            // An empty base file is also a rejection, including an interrupted AtomicFile write.
            if (!marker.baseFile.exists() && !marker.baseFile.createNewFile()) throw unsupportedFormat()
            val output = marker.startWrite()
            try {
                output.write(1)
                output.fd.sync()
                marker.finishWrite(output)
            } catch (_: Exception) {
                marker.failWrite(output)
                throw unsupportedFormat()
            }
        } catch (_: Exception) {
            throw unsupportedFormat()
        }
    }

    private fun marker() = AtomicFile(File(noBackupDirectory(), ".logdate_native_restore_format_rejected"))

    private fun markerFiles(base: File) = listOf(base, File(base.path + ".new"), File(base.path + ".bak"))

    private fun unsupportedFormat() = IOException("Native restore payload format is not supported")
}
