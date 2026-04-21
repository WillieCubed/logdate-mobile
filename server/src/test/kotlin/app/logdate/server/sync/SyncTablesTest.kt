package app.logdate.server.sync

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Unit tests for the synchronization database table definitions.
 *
 * This class ensures that the underlying database schema for synchronization,
 * particularly the backup-related tables, exposes the necessary columns for
 * tracking encryption versions, modes, and key identifiers.
 */
class SyncTablesTest {
    @Test
    fun `backup table encryption columns are exposed`() {
        assertNotNull(BackupSyncTable.encryptionVersion)
        assertNotNull(BackupSyncTable.encryptionKeyId)
        assertNotNull(BackupSyncTable.encryptionMode)
    }
}
