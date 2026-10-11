package app.logdate.server.identity

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AtRestKeyCipherTest {
    private val plaintext = "private key bytes".encodeToByteArray()

    @Test
    fun `decrypts values encrypted with a decrypt-only key`() {
        val previous = AtRestKeyCipher(OLD_KEK).encrypt(plaintext)

        val cipher = AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK))

        assertContentEquals(plaintext, cipher.decrypt(previous))
    }

    @Test
    fun `encrypts only with the encrypting key`() {
        val encrypted = AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK)).encrypt(plaintext)

        assertContentEquals(plaintext, AtRestKeyCipher(NEW_KEK).decrypt(encrypted))
        assertFailsWith<AEADBadTagException> { AtRestKeyCipher(OLD_KEK).decrypt(encrypted) }
    }

    @Test
    fun `rewrap leaves values the encrypting key already encrypted`() {
        val cipher = AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK))

        assertNull(cipher.rewrap(cipher.encrypt(plaintext)))
    }

    @Test
    fun `rewrap re-encrypts values from a decrypt-only key under the encrypting key`() {
        val previous = AtRestKeyCipher(OLD_KEK).encrypt(plaintext)

        val rewrapped = assertNotNull(AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK)).rewrap(previous))

        assertContentEquals(plaintext, AtRestKeyCipher(NEW_KEK).decrypt(rewrapped))
    }

    @Test
    fun `values no known key encrypted are rejected`() {
        val foreign = AtRestKeyCipher(OTHER_KEK).encrypt(plaintext)
        val cipher = AtRestKeyCipher(NEW_KEK, decryptOnlySeeds = listOf(OLD_KEK))

        assertFailsWith<AEADBadTagException> { cipher.decrypt(foreign) }
        assertFailsWith<AEADBadTagException> { cipher.rewrap(foreign) }
    }

    private companion object {
        const val OLD_KEK = "old-key-encryption-key-0123456789abcdef"
        const val NEW_KEK = "new-key-encryption-key-0123456789abcdef"
        const val OTHER_KEK = "unrelated-key-encryption-key-0123456789"
    }
}
