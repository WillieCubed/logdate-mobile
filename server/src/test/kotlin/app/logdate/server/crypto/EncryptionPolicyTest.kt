package app.logdate.server.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptionPolicyTest {
    @Test
    fun `AT_REST_ONLY accepts plaintext and encrypts`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.AT_REST_ONLY,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val plaintext = "plaintext data".toByteArray()
        val decision = policy.evaluate(plaintext)

        assertEquals(PolicyDecision.EncryptAtRest, decision)
    }

    @Test
    fun `AT_REST_ONLY accepts client ciphertext with passthrough`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.AT_REST_ONLY,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val clientCiphertext = "LDCE1".toByteArray() + ByteArray(28)
        val decision = policy.evaluate(clientCiphertext)

        assertEquals(PolicyDecision.AcceptClientCiphertext, decision)
    }

    @Test
    fun `AT_REST_ONLY accepts server ciphertext`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.AT_REST_ONLY,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val serverCiphertext = "LDSM1".toByteArray() + ByteArray(28)
        val decision = policy.evaluate(serverCiphertext)

        assertEquals(PolicyDecision.AcceptServerCiphertext, decision)
    }

    @Test
    fun `E2EE_REQUIRED rejects plaintext`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.E2EE_REQUIRED,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val plaintext = "plaintext data".toByteArray()
        val decision = policy.evaluate(plaintext)

        assertTrue(decision is PolicyDecision.Reject)
        assertTrue((decision as PolicyDecision.Reject).reason.contains("E2EE required"))
    }

    @Test
    fun `E2EE_REQUIRED accepts client ciphertext`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.E2EE_REQUIRED,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val clientCiphertext = "LDCE1".toByteArray() + ByteArray(28)
        val decision = policy.evaluate(clientCiphertext)

        assertEquals(PolicyDecision.AcceptClientCiphertext, decision)
    }

    @Test
    fun `E2EE_REQUIRED accepts server ciphertext`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.E2EE_REQUIRED,
            serverEncryptionEnabled = true,
            allowPassthroughClientCiphertext = true
        )

        val serverCiphertext = "LDSM1".toByteArray() + ByteArray(28)
        val decision = policy.evaluate(serverCiphertext)

        assertEquals(PolicyDecision.AcceptServerCiphertext, decision)
    }

    @Test
    fun `AT_REST_ONLY with encryption disabled accepts plaintext`() {
        val policy = EncryptionPolicy(
            mode = EncryptionMode.AT_REST_ONLY,
            serverEncryptionEnabled = false,
            allowPassthroughClientCiphertext = true
        )

        val plaintext = "plaintext data".toByteArray()
        val decision = policy.evaluate(plaintext)

        assertEquals(PolicyDecision.AcceptPlaintext, decision)
    }
}
