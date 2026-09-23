package app.logdate.client.device.crypto

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AndroidIdentityKeyBackupStore], which mirrors the identity recovery phrase into a
 * file that survives what [app.logdate.client.device.storage.SecureStorage] (KeyStore-bound)
 * cannot: a device-to-device transfer or a cloud restore onto a new phone.
 *
 * These tests run on the JVM via `androidHostTest` - no emulator, no Robolectric. [Context] is
 * mocked directly and pointed at a real temp directory, matching the pattern already used for
 * local unit tests elsewhere in this codebase (e.g. `AndroidNetworkSaverModeProviderTest`).
 */
class AndroidIdentityKeyBackupStoreTest {
    private val tempDir = createTempDirectory("identity-key-backup-test").toFile()
    private val context =
        mockk<Context>(relaxed = true).also {
            every { it.filesDir } returns tempDir
        }
    private val store = AndroidIdentityKeyBackupStore(context)

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `read returns null when nothing has been written`() =
        runTest {
            assertNull(store.readPhrase())
        }

    @Test
    fun `write then read round-trips the phrase`() =
        runTest {
            val phrase = (1..12).joinToString(" ") { "word$it" }

            store.writePhrase(phrase)

            assertEquals(phrase, store.readPhrase())
        }

    @Test
    fun `write creates the backup file under identity_recovery`() =
        runTest {
            store.writePhrase("a b c")

            val backupFile = File(tempDir, "identity_recovery/phrase_backup")
            assertTrue(backupFile.exists())
            assertEquals("a b c", backupFile.readText())
        }

    @Test
    fun `write overwrites a previous backup`() =
        runTest {
            store.writePhrase("first phrase")
            store.writePhrase("second phrase")

            assertEquals("second phrase", store.readPhrase())
        }

    @Test
    fun `clear removes the backup file`() =
        runTest {
            store.writePhrase("a b c")

            store.clear()

            assertNull(store.readPhrase())
            assertTrue(!File(tempDir, "identity_recovery/phrase_backup").exists())
        }

    @Test
    fun `clear is safe to call when nothing was written`() =
        runTest {
            store.clear()

            assertNull(store.readPhrase())
        }
}
