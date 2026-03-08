package app.logdate.server.sync

import kotlin.test.Test
import kotlin.test.assertNotNull

class SyncTablesTest {
    @Test
    fun `backup table encryption columns are exposed`() {
        assertNotNull(BackupSyncTable.encryptionVersion)
        assertNotNull(BackupSyncTable.encryptionKeyId)
        assertNotNull(BackupSyncTable.encryptionMode)
    }
}
