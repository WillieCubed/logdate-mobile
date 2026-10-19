package app.logdate.integration.e2e.fixtures

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.time.Clock

/**
 * The DPoP key a third-party AT Protocol app holds. Each proof binds one request (method and
 * URL) to this key, and tokens issued to the app only work alongside proofs from the same key.
 */
internal class DpopKey {
    private val keyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val random = SecureRandom()

    /** Signs a proof for [method] [htu]; [accessToken] adds the `ath` claim that resource requests need. */
    fun proof(
        method: String,
        htu: String,
        nonce: String? = null,
        accessToken: String? = null,
    ): String {
        val publicKey = keyPair.public as ECPublicKey
        val jwk =
            """{"kty":"EC","crv":"P-256","x":"${publicKey.w.affineX.coordinate().b64u()}","y":"${publicKey.w.affineY.coordinate().b64u()}"}"""
        val header = """{"typ":"dpop+jwt","alg":"ES256","jwk":$jwk}"""
        val claims =
            buildString {
                append("""{"jti":"${ByteArray(16).also(random::nextBytes).b64u()}","htm":"$method","htu":"$htu"""")
                append(""","iat":${Clock.System.now().epochSeconds}""")
                nonce?.let { append(""","nonce":"$it"""") }
                accessToken?.let { append(""","ath":"${sha256(it.toByteArray()).b64u()}"""") }
                append('}')
            }
        val signingInput = "${header.toByteArray().b64u()}.${claims.toByteArray().b64u()}"
        val signature =
            Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initSign(keyPair.private)
                update(signingInput.toByteArray())
                sign()
            }
        return "$signingInput.${signature.b64u()}"
    }
}

/** The PKCE `S256` challenge for [verifier]. */
internal fun pkceChallenge(verifier: String): String = sha256(verifier.toByteArray()).b64u()

private fun BigInteger.coordinate(): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == COORDINATE_BYTES -> raw
        raw.size > COORDINATE_BYTES -> raw.copyOfRange(raw.size - COORDINATE_BYTES, raw.size)
        else -> ByteArray(COORDINATE_BYTES - raw.size) + raw
    }
}

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

private fun ByteArray.b64u(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private const val COORDINATE_BYTES = 32
