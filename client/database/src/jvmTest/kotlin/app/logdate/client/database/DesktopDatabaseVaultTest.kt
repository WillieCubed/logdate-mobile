package app.logdate.client.database

import app.logdate.client.device.storage.DesktopFileCipher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDatabaseVaultTest {
    private val directory = Files.createTempDirectory("desktop-vault")
    private val database = directory.resolve("logdate")
    private val sealed = directory.resolve("logdate.enc")

    /** Reverses the bytes so a sealed file differs from its plaintext; a failure leaves a partial file. */
    private class ReversingCipher(
        private val failDecrypt: Boolean = false,
        private val failEncrypt: Boolean = false,
    ) : DesktopFileCipher {
        override fun encryptFile(
            source: Path,
            target: Path,
        ) = reverse(source, target, fail = failEncrypt)

        override fun decryptFile(
            source: Path,
            target: Path,
        ) = reverse(source, target, fail = failDecrypt)

        private fun reverse(
            source: Path,
            target: Path,
            fail: Boolean,
        ) {
            val reversed = Files.readAllBytes(source).reversedArray()
            Files.write(target, if (fail) reversed.copyOf(2) else reversed)
            check(!fail) { "cipher failed" }
        }
    }

    @Test
    fun `a sealed database that cannot be decrypted stops startup and stays unchanged`() {
        val original = "sealed journal".encodeToByteArray()
        Files.write(sealed, original)

        val vault = DesktopDatabaseVault(ReversingCipher(failDecrypt = true), database)

        assertFailsWith<EncryptedDatabaseUnavailableException> { vault.unseal() }

        assertFalse(Files.exists(database), "an empty replacement database would be created over the sealed journal")
        assertContentEquals(original, Files.readAllBytes(sealed))
        assertTrue(Files.list(directory).use { it.count() } == 1L, "a partial decryption was left behind")
    }

    @Test
    fun `sealing then unsealing returns the same database`() {
        val content = "journal pages".encodeToByteArray()
        Files.write(database, content)
        val vault = DesktopDatabaseVault(ReversingCipher(), database)

        vault.seal()
        assertFalse(Files.exists(database))
        vault.unseal()

        assertContentEquals(content, Files.readAllBytes(database))
    }

    @Test
    fun `a database that cannot be sealed is kept unencrypted and the old sealed copy is kept`() {
        val content = "journal pages".encodeToByteArray()
        val previous = "previous seal".encodeToByteArray()
        Files.write(database, content)
        Files.write(sealed, previous)

        DesktopDatabaseVault(ReversingCipher(failEncrypt = true), database).seal()

        assertContentEquals(content, Files.readAllBytes(database))
        assertContentEquals(previous, Files.readAllBytes(sealed))
    }

    @Test
    fun `a database with unsaved log pages is not sealed`() {
        Files.write(database, "journal pages".encodeToByteArray())
        Files.write(database.resolveSibling("logdate-wal"), "pending".encodeToByteArray())

        DesktopDatabaseVault(ReversingCipher(), database).seal()

        assertTrue(Files.exists(database))
        assertFalse(Files.exists(sealed))
    }
}
