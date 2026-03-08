package app.logdate.server.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptionServiceTest {
    @Test
    fun `encryption service exposes processed payload encryption flag`() {
        val policy =
            EncryptionPolicy(
                mode = EncryptionMode.AT_REST_ONLY,
                serverEncryptionEnabled = false,
                allowPassthroughClientCiphertext = true,
            )
        val service = EncryptionService(policy = policy, codec = PayloadCodec(NoOpKeyring))

        val processed = service.processMediaUpload("plain".toByteArray(), "user", "media", "content")
        assertFalse(processed.encrypted)
    }

    @Test
    fun `reject policy throws encryption policy exception`() {
        val policy =
            EncryptionPolicy(
                mode = EncryptionMode.E2EE_REQUIRED,
                serverEncryptionEnabled = true,
                allowPassthroughClientCiphertext = false,
            )
        val service = EncryptionService(policy = policy, codec = PayloadCodec(NoOpKeyring))

        val error =
            assertFailsWith<EncryptionPolicyException> {
                service.processMediaUpload("plain".toByteArray(), "user", "media", "content")
            }
        assertEquals("E2EE required: plaintext not allowed", error.message)
    }

    @Test
    fun `backup upload and download branches preserve plaintext when decrypt disabled`() {
        val policy =
            EncryptionPolicy(
                mode = EncryptionMode.AT_REST_ONLY,
                serverEncryptionEnabled = true,
                allowPassthroughClientCiphertext = true,
            )
        val service = EncryptionService(policy = policy, codec = PayloadCodec(NoOpKeyring))
        val payload = "backup-plain".toByteArray()

        val uploaded = service.processBackupUpload(payload, "user", "backup")
        assertTrue(uploaded.encrypted)

        val passthrough = service.processBackupDownload(uploaded.data, shouldDecrypt = false)
        assertTrue(uploaded.data.contentEquals(passthrough))

        val restored = service.processBackupDownload(uploaded.data, shouldDecrypt = true)
        assertTrue(payload.contentEquals(restored))
    }
}
