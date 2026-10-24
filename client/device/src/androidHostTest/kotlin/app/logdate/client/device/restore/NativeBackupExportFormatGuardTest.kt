package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

class NativeBackupExportFormatGuardTest {
    @Test
    fun `cross-platform export cannot fall through to raw Android file delivery`() {
        assertFailsWith<IOException> {
            requireNativeAndroidExport(BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS)
        }
    }

    @Test
    fun `encrypted cross-platform export remains refused until its portable adapter is ready`() {
        assertFailsWith<IOException> {
            requireNativeAndroidExport(
                BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS or
                    BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or
                    BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER,
            )
        }
    }

    @Test
    fun `ordinary encrypted Android cloud and device transfers remain available`() {
        requireNativeAndroidExport(0)
        requireNativeAndroidExport(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED)
        requireNativeAndroidExport(BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER)
        requireNativeAndroidExport(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER)
    }
}
