package app.logdate.client.device.restore

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeBackupQuotaStoreTest {
    private val directory = createTempDirectory("native-backup-quota").toFile()
    private val store = NativeBackupQuotaStore(directory)

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `platform quota failure survives a new store instance`() {
        store.record(30_000_000, 25_000_000)
        assertEquals(NativeBackupQuotaFailure(30_000_000, 25_000_000), NativeBackupQuotaStore(directory).read())
    }

    @Test
    fun `missing failure record leaves platform backup status unconfirmed`() {
        assertNull(store.read())
    }

    @Test
    fun `malformed failure records cannot crash backup settings`() {
        val file = File(directory, ".logdate_native_backup_quota_exceeded")
        for (value in listOf("bad", "1:2:3", "-1:25", "25:0", "9223372036854775808:25", "x".repeat(1_000))) {
            file.writeText(value)
            assertNull(store.read(), value)
        }
    }
}
