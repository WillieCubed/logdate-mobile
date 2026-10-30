package app.logdate.client.device.storage

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DesktopSecureStorageFileTest {
    private val directory = Files.createTempDirectory("secure-storage")

    @Test
    fun `a file encrypted in place decrypts to the same bytes`() {
        val storage = DesktopSecureStorage(directory)
        val plain = directory.resolve("journal.db")
        val sealed = directory.resolve("journal.db.enc")
        val restored = directory.resolve("journal.db.restored")
        val content = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        Files.write(plain, content)

        storage.encryptFile(plain, sealed)
        storage.decryptFile(sealed, restored)

        assertContentEquals(content, Files.readAllBytes(restored))
    }

    @Test
    fun `a database sealed by an earlier version still opens`() =
        runTest {
            val storage = DesktopSecureStorage(directory)
            val content = "existing journal".encodeToByteArray()
            val sealed = directory.resolve("journal.db.enc")
            Files.write(sealed, storage.encrypt(content))

            storage.decryptFile(sealed, directory.resolve("journal.db"))

            assertContentEquals(content, Files.readAllBytes(directory.resolve("journal.db")))
        }

    @Test
    fun `a file sealed with another key is refused`() {
        val sealed = directory.resolve("journal.db.enc")
        val plain = directory.resolve("journal.db")
        Files.write(plain, "existing journal".encodeToByteArray())
        DesktopSecureStorage(Files.createTempDirectory("other-key")).encryptFile(plain, sealed)

        assertFailsWith<Exception> { DesktopSecureStorage(directory).decryptFile(sealed, directory.resolve("restored.db")) }
    }

    @Test
    fun `an unreadable device key stops instead of replacing the key`() {
        Files.write(directory.resolve("secure-storage.key"), ByteArray(3))

        assertFailsWith<IllegalStateException> { DesktopSecureStorage(directory) }
    }
}
