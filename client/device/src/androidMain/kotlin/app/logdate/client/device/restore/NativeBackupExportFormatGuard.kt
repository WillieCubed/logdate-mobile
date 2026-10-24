package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import java.io.IOException

/** Raw Android database delivery cannot satisfy the private cross-platform journal protocol. */
internal fun requireNativeAndroidExport(transportFlags: Int) {
    if (transportFlags and BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS != 0) {
        throw IOException("Cross-platform journal export is not enabled")
    }
}
