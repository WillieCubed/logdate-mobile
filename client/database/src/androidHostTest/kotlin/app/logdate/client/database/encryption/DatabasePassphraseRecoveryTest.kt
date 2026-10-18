package app.logdate.client.database.encryption

import android.content.Context
import app.logdate.client.device.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DatabasePassphraseRecoveryTest {
    private val directory = createTempDirectory("database-key-recovery").toFile()
    private val context = mockk<Context>().also { every { it.filesDir } returns directory }
    private val store = PassphraseBackupStore(context)
    private val secureStorage = mockk<SecureStorage>(relaxed = true)

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `valid primary remains available when portable recovery material is malformed`() =
        runTest {
            val primary = ByteArray(32) { 7 }
            val file =
                File(directory, "db_recovery/passphrase_backup").apply {
                    parentFile?.mkdirs()
                    writeText("truncated-key")
                }
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(primary)

            val candidates = DatabasePassphraseProvider(secureStorage, store) { true }.getPassphraseCandidates()

            assertContentEquals(primary, candidates.single())
            assertEquals("truncated-key", file.readText())
        }

    @Test
    fun `missing primary cannot mint over malformed recovery material even without a database`() =
        runTest {
            val file =
                File(directory, "db_recovery/passphrase_backup").apply {
                    parentFile?.mkdirs()
                    writeText("truncated-key")
                }
            coEvery { secureStorage.getString("db_encryption_key") } returns null
            assertFailsWith<IllegalStateException> { DatabasePassphraseProvider(secureStorage, store).getPassphraseCandidates() }
            assertEquals("truncated-key", file.readText())
            coVerify(exactly = 0) { secureStorage.putString(any(), any()) }
        }

    @Test
    fun `retained database without a key never generates replacement key material`() =
        runTest {
            coEvery { secureStorage.getString("db_encryption_key") } returns null
            val original = File(directory, "retained-database").apply { writeBytes(byteArrayOf(8, 9, 10)) }
            val provider = DatabasePassphraseProvider(secureStorage, store) { original.exists() }

            assertFailsWith<IllegalStateException> { provider.getPassphraseCandidates() }

            assertContentEquals(byteArrayOf(8, 9, 10), original.readBytes())
            assertFalse(File(directory, "db_recovery/passphrase_backup").exists())
            coVerify(exactly = 0) { secureStorage.putString(any(), any()) }
        }

    @Test
    fun `malformed primary key recovers the intact portable backup`() =
        runTest {
            val expected = ByteArray(32) { it.toByte() }
            store.write(expected)
            coEvery { secureStorage.getString("db_encryption_key") } returns "not-valid-base64!!"
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }

            val candidate = provider.getPassphraseCandidates().single()
            assertContentEquals(expected, candidate)
            coVerify(exactly = 0) { secureStorage.putString(any(), any()) }
            provider.confirmOpenedDatabasePassphrase(candidate)
            assertContentEquals(expected, store.read())
            coVerify { secureStorage.putString("db_encryption_key", Base64.encode(expected)) }
        }

    @Test
    fun `wrong length primary does not overwrite a recoverable backup`() =
        runTest {
            val expected = ByteArray(32) { 7 }
            store.write(expected)
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(byteArrayOf(1, 2))
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }

            assertContentEquals(expected, provider.getPassphraseCandidates().single())
            assertContentEquals(expected, store.read())
        }

    @Test
    fun `conflicting valid keys are both tried without changing either copy`() =
        runTest {
            val backup = ByteArray(32) { 7 }
            val primary = ByteArray(32) { 8 }
            store.write(backup)
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(primary)
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }

            val candidates = provider.getPassphraseCandidates()

            assertEquals(2, candidates.size)
            assertContentEquals(primary, candidates[0])
            assertContentEquals(backup, candidates[1])
            assertContentEquals(backup, store.read())
            coVerify(exactly = 0) { secureStorage.putString(any(), any()) }
        }

    @Test
    fun `failed backup replacement retains the previous key and reports failure`() {
        val original = ByteArray(32) { 7 }
        store.write(original)
        File(directory, "db_recovery/passphrase_backup.part").mkdir()

        assertFailsWith<IOException> { store.write(ByteArray(32) { 8 }) }

        assertContentEquals(original, store.read())
    }

    @Test
    fun `valid key candidates are tried without changing either recovery copy`() =
        runTest {
            val primary = ByteArray(32) { 7 }
            val backup = ByteArray(32) { 8 }
            store.write(backup)
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(primary)
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }

            val candidates = provider.getPassphraseCandidates()

            assertEquals(2, candidates.size)
            assertContentEquals(primary, candidates[0])
            assertContentEquals(backup, candidates[1])
            assertContentEquals(backup, store.read())
            coVerify(exactly = 0) { secureStorage.putString(any(), any()) }
        }

    @Test
    fun `malformed portable key does not block opening with an intact primary`() =
        runTest {
            val primary = ByteArray(32) { 7 }
            val file =
                File(directory, "db_recovery/passphrase_backup").apply {
                    parentFile?.mkdirs()
                    writeText("truncated-key")
                }
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(primary)
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }

            val candidates = provider.getPassphraseCandidates()

            assertEquals(1, candidates.size)
            assertContentEquals(primary, candidates.single())
            assertEquals("truncated-key", file.readText())
        }

    @Test
    fun `selected database key updates both stores only after the database opens`() =
        runTest {
            val primary = ByteArray(32) { 7 }
            val backup = ByteArray(32) { 8 }
            store.write(backup)
            coEvery { secureStorage.getString("db_encryption_key") } returns Base64.encode(primary)
            val provider = DatabasePassphraseProvider(secureStorage, store) { true }
            val candidates = provider.getPassphraseCandidates()

            provider.confirmOpenedDatabasePassphrase(candidates[1])

            assertContentEquals(backup, store.read())
            coVerify { secureStorage.putString("db_encryption_key", Base64.encode(backup)) }
        }
}
