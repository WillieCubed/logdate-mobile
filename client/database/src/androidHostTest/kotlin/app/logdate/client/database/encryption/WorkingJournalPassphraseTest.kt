package app.logdate.client.database.encryption

import android.content.Context
import app.logdate.client.device.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkingJournalPassphraseTest {
    @Test
    fun `a separate working key cannot read or overwrite the original database key bytes`() =
        runTest {
            val directory = Files.createTempDirectory("working-journal-key-").toFile()
            try {
                val original = ByteArray(32) { 7 }
                val context = mockk<Context> { every { filesDir } returns directory }
                val oldBackup =
                    File(directory, "db_recovery/passphrase_backup").apply {
                        parentFile.mkdirs()
                        writeText(Base64.encode(original))
                    }
                val secure = mockk<SecureStorage>(relaxed = true)
                coEvery { secure.getString("db_encryption_key.working.fixture") } returns null
                val portable = PassphraseBackupStore(context) { File(directory, "db_recovery/working_passphrase_backup") }
                val provider = DatabasePassphraseProvider(secure, portable, storageKey = { "db_encryption_key.working.fixture" }) { false }
                val working = provider.getPassphraseCandidates().single()
                assertFalse(working.contentEquals(original))
                assertContentEquals(Base64.encode(original).encodeToByteArray(), oldBackup.readBytes())
                assertTrue(File(directory, "db_recovery/working_passphrase_backup").exists())
                coVerify(exactly = 0) { secure.getString("db_encryption_key") }
                coVerify(exactly = 0) { secure.putString("db_encryption_key", any()) }
                coVerify(exactly = 0) { secure.remove(any()) }
            } finally {
                directory.deleteRecursively()
            }
        }
}
