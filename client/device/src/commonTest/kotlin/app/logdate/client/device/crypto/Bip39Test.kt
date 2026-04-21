package app.logdate.client.device.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the BIP-39 implementation, ensuring compliance with the Bitcoin Improvement
 * Proposal for deterministic mnemonic phrases.
 *
 * This suite verifies the conversion between entropy and mnemonic words, validates
 * checksums, and confirms compatibility with known test vectors from the BIP-39
 * specification.
 */
class Bip39Test {
    @Test
    fun testGenerateMnemonic() {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = Bip39.generateMnemonic(entropy)

        assertEquals(12, mnemonic.size)
        assertTrue(mnemonic.all { it.isNotBlank() })
    }

    @Test
    fun testMnemonicRoundtrip() {
        val originalEntropy = ByteArray(16) { it.toByte() }
        val mnemonic = Bip39.generateMnemonic(originalEntropy)
        val recoveredEntropy = Bip39.mnemonicToEntropy(mnemonic)

        assertTrue(originalEntropy.contentEquals(recoveredEntropy))
    }

    @Test
    fun testValidateMnemonic() {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = Bip39.generateMnemonic(entropy)

        assertTrue(Bip39.validateMnemonic(mnemonic))
    }

    @Test
    fun testValidateInvalidMnemonic() {
        val invalidMnemonic =
            listOf("invalid", "word", "list", "that", "is", "not", "real", "bip39", "mnemonic", "phrase", "example", "test")

        assertFalse(Bip39.validateMnemonic(invalidMnemonic))
    }

    @Test
    fun testKnownMnemonic() {
        // Known test vector from BIP-39 spec
        val knownMnemonic =
            listOf(
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "abandon",
                "about",
            )

        assertTrue(Bip39.validateMnemonic(knownMnemonic))

        val entropy = Bip39.mnemonicToEntropy(knownMnemonic)
        assertEquals(16, entropy.size)
    }
}
