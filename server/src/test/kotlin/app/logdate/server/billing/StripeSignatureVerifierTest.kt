package app.logdate.server.billing

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StripeSignatureVerifierTest {
    private val secret = "whsec_test_abc_123"
    private val verifier = StripeSignatureVerifier(secret)

    @Test
    fun `accepts a signature Stripe would have produced`() {
        val timestamp = 1_700_000_000L
        val body = """{"id":"evt_1","type":"checkout.session.completed"}"""
        val header = "t=$timestamp,v1=${hmacHex(secret, "$timestamp.$body")}"

        val result = verifier.verify(body, header, nowEpochSeconds = timestamp)

        assertIs<StripeSignatureVerifier.Verification.Ok>(result)
    }

    @Test
    fun `rejects a tampered body`() {
        val timestamp = 1_700_000_000L
        val originalBody = """{"amount":500}"""
        val tamperedBody = """{"amount":5}"""
        val header = "t=$timestamp,v1=${hmacHex(secret, "$timestamp.$originalBody")}"

        val result = verifier.verify(tamperedBody, header, nowEpochSeconds = timestamp)

        assertIs<StripeSignatureVerifier.Verification.Rejected>(result)
        assertTrue(result.reason.contains("Signature mismatch"))
    }

    @Test
    fun `rejects a signature from outside the tolerance window`() {
        val timestamp = 1_700_000_000L
        val body = "body"
        val header = "t=$timestamp,v1=${hmacHex(secret, "$timestamp.$body")}"

        val result = verifier.verify(body, header, nowEpochSeconds = timestamp + 1_000)

        assertIs<StripeSignatureVerifier.Verification.Rejected>(result)
        assertTrue(result.reason.contains("tolerance"))
    }

    @Test
    fun `rejects when header is missing`() {
        val result = verifier.verify("body", signatureHeader = null, nowEpochSeconds = 0L)
        assertIs<StripeSignatureVerifier.Verification.Rejected>(result)
    }

    @Test
    fun `accepts when any of several v1 signatures matches for key rotation`() {
        val timestamp = 1_700_000_000L
        val body = "body"
        val bogus = "0".repeat(64)
        val real = hmacHex(secret, "$timestamp.$body")
        val header = "t=$timestamp,v1=$bogus,v1=$real"

        val result = verifier.verify(body, header, nowEpochSeconds = timestamp)

        assertIs<StripeSignatureVerifier.Verification.Ok>(result)
    }

    private fun hmacHex(
        secret: String,
        payload: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
