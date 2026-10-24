package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeIosTransferAdmissionTest {
    @Test
    fun acceptsOnlyEncryptedCanonicalIosTransfer() {
        requireNativeIosExport(
            BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS or BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED,
        )
        assertFailsWith<Exception> {
            requireNativeIosExport(BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS)
        }
        assertFailsWith<Exception> {
            requireNativeIosExport(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED)
        }
    }
}
