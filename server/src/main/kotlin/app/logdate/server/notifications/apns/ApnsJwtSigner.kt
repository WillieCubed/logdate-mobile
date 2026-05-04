package app.logdate.server.notifications.apns

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64

/**
 * Generates and caches the ES256-signed JWT that APNs requires on every push request.
 * The token is valid for up to an hour but must be at least 20 minutes old before it's
 * reused — APNs rejects tokens younger than that. We rotate at the 50-minute mark to
 * stay safely inside both bounds.
 */
class ApnsJwtSigner(
    private val teamId: String,
    private val keyId: String,
    privateKeyPem: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val privateKey: PrivateKey = parsePkcs8PemKey(privateKeyPem)

    @Volatile
    private var cached: CachedToken? = null

    fun token(): String {
        val now = clock.instant().epochSecond
        val current = cached
        if (current != null && now - current.issuedAt < REUSE_WINDOW_SECONDS) {
            return current.value
        }
        val token = mint(now)
        cached = CachedToken(token, now)
        return token
    }

    private fun mint(issuedAt: Long): String {
        val header = """{"alg":"ES256","kid":"$keyId","typ":"JWT"}"""
        val payload = """{"iss":"$teamId","iat":$issuedAt}"""
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val signingInput =
            "${encoder.encodeToString(header.toByteArray(Charsets.UTF_8))}." +
                encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(signingInput.toByteArray(Charsets.UTF_8))
        val derSignature = signer.sign()
        // APNs / JOSE requires the raw P1363 (r || s) form, not the DER-encoded ECDSA blob.
        val rawSignature = derToP1363(derSignature, P1363_COMPONENT_BYTES)
        return "$signingInput.${encoder.encodeToString(rawSignature)}"
    }

    private data class CachedToken(
        val value: String,
        val issuedAt: Long,
    )

    companion object {
        private const val REUSE_WINDOW_SECONDS = 50L * 60L
        private const val P1363_COMPONENT_BYTES = 32

        private fun parsePkcs8PemKey(pem: String): PrivateKey {
            val cleaned =
                pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(cleaned)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            return KeyFactory.getInstance("EC").generatePrivate(spec)
        }

        /**
         * Convert a DER-encoded ECDSA signature into the P1363 raw `r || s` form expected by
         * JOSE's ES256. Apple does not document this requirement explicitly, but their JWT
         * verifier rejects DER-formatted signatures.
         */
        private fun derToP1363(
            der: ByteArray,
            componentBytes: Int,
        ): ByteArray {
            require(der.size >= 8 && der[0] == 0x30.toByte()) { "Not a DER ECDSA signature" }
            var offset = 2
            if ((der[1].toInt() and 0x80) != 0) offset += der[1].toInt() and 0x7f
            require(der[offset] == 0x02.toByte()) { "Expected r INTEGER" }
            val rLen = der[offset + 1].toInt()
            val rStart = offset + 2
            offset = rStart + rLen
            require(der[offset] == 0x02.toByte()) { "Expected s INTEGER" }
            val sLen = der[offset + 1].toInt()
            val sStart = offset + 2

            val r = trimAndPad(der.copyOfRange(rStart, rStart + rLen), componentBytes)
            val s = trimAndPad(der.copyOfRange(sStart, sStart + sLen), componentBytes)
            return r + s
        }

        private fun trimAndPad(
            value: ByteArray,
            componentBytes: Int,
        ): ByteArray {
            val trimmed = value.dropWhile { it == 0.toByte() }.toByteArray()
            require(trimmed.size <= componentBytes) { "ECDSA component longer than $componentBytes bytes" }
            return ByteArray(componentBytes - trimmed.size) + trimmed
        }
    }
}
