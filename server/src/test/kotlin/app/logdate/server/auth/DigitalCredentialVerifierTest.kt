package app.logdate.server.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Exercises [DigitalCredentialVerifier] against locally-generated SD-JWT VCs.
 *
 * Why generate fresh credentials rather than checking in fixed test vectors:
 *  - Real Google-signed test vectors require enrollment in the Verifier Registry
 *    and have hard-coded expiry; vectors generated here are deterministic for the
 *    test (controlled clock + UUIDs are the only nondeterminism, both managed).
 *  - The crypto primitives (ECDSA over P-256, SHA-256 hash, base64url disclosure
 *    encoding) are identical to what Google emits; the only thing we cannot test
 *    here is Google's specific signing key.
 *
 * The verifier's trust anchor is overridden via a custom loader on
 * [GoogleVcJwksCache] that returns our test issuer key.
 */
class DigitalCredentialVerifierTest {
    private val issuerKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-issuer-key").generate()
    private val holderKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-holder-key").generate()
    private val fixedNow: Instant = Instant.fromEpochSeconds(1_775_083_422)
    private val expectedAudience = "https://logdate.app/auth/email"
    private val expectedNonce = "test-nonce-xyz"

    private val jwksCache =
        GoogleVcJwksCache(
            jwksUrl = URI.create("https://example.invalid/jwks").toURL(),
            ttl = 1.hours,
            clock = { fixedNow },
            loader = { JWKSet(issuerKey.toPublicJWK()) },
        )

    private val verifier =
        DigitalCredentialVerifier(
            jwksCache = jwksCache,
            expectedAudience = expectedAudience,
            clock = { fixedNow },
        )

    // --- happy path --------------------------------------------------------

    @Test
    fun `verifies a well-formed Google-style credential`() =
        runBlocking {
            val credential =
                buildCredentialJson(
                    disclosed =
                        mapOf(
                            "email" to JsonPrimitive("jane.doe@example.com"),
                            "email_verified" to JsonPrimitive(true),
                            "name" to JsonPrimitive("Jane Doe"),
                            "given_name" to JsonPrimitive("Jane"),
                            "family_name" to JsonPrimitive("Doe"),
                            "picture" to JsonPrimitive("https://example.com/jane.jpg"),
                            "hd" to JsonPrimitive(""),
                        ),
                )

            val result = verifier.verify(credential, expectedNonce)

            val claims = assertNotNull(result.getOrNull(), "expected success: ${result.exceptionOrNull()?.message}")
            assertEquals("jane.doe@example.com", claims.email)
            assertEquals("Jane Doe", claims.name)
            assertEquals("Jane", claims.givenName)
            assertEquals("Doe", claims.familyName)
            assertEquals("https://example.com/jane.jpg", claims.picture)
            assertEquals("", claims.hostedDomain)
            assertEquals(fixedNow, claims.verifiedAt)
        }

    // --- negative paths ----------------------------------------------------

    @Test
    fun `rejects credential whose issuer is not Google`() =
        runBlocking {
            val credential = buildCredentialJson(issuerOverride = "https://attacker.example/")
            assertRejected(verifier.verify(credential, expectedNonce), "untrusted_issuer")
        }

    @Test
    fun `rejects credential whose vct is not UserInfoCredential`() =
        runBlocking {
            val credential = buildCredentialJson(vctOverride = "EvilCredential")
            assertRejected(verifier.verify(credential, expectedNonce), "unexpected_vct")
        }

    @Test
    fun `rejects credential signed by an unknown kid`() =
        runBlocking {
            val strangerKey = ECKeyGenerator(Curve.P_256).keyID("stranger-key").generate()
            val credential = buildCredentialJson(signWithOverride = strangerKey)
            assertRejected(verifier.verify(credential, expectedNonce), "unknown_kid")
        }

    @Test
    fun `rejects credential with tampered issuer signature`() =
        runBlocking {
            val credential = buildCredentialJson()
            val tampered = flipLastCharOfFirstSegment(credential)
            // Result may also surface as malformed_issuer_jwt_payload if base64 corrupts cleanly;
            // either way it must not succeed.
            val result = verifier.verify(tampered, expectedNonce)
            assertTrue(result.isFailure, "expected failure")
        }

    @Test
    fun `rejects credential whose exp is in the past`() =
        runBlocking {
            val credential =
                buildCredentialJson(
                    issuerExp = fixedNow.epochSeconds - 10.minutes.inWholeSeconds,
                )
            assertRejected(verifier.verify(credential, expectedNonce), "expired")
        }

    @Test
    fun `rejects credential whose iat is in the future`() =
        runBlocking {
            val credential =
                buildCredentialJson(
                    issuerIat = fixedNow.epochSeconds + 30.minutes.inWholeSeconds,
                )
            assertRejected(verifier.verify(credential, expectedNonce), "issued_in_future")
        }

    @Test
    fun `rejects credential when email_verified is false`() =
        runBlocking {
            val credential =
                buildCredentialJson(
                    disclosed =
                        mapOf(
                            "email" to JsonPrimitive("not-verified@example.com"),
                            "email_verified" to JsonPrimitive(false),
                        ),
                )
            assertRejected(verifier.verify(credential, expectedNonce), "email_not_verified")
        }

    @Test
    fun `rejects credential without an email_verified claim`() =
        runBlocking {
            val credential =
                buildCredentialJson(
                    disclosed =
                        mapOf(
                            "email" to JsonPrimitive("nobody@example.com"),
                        ),
                )
            assertRejected(verifier.verify(credential, expectedNonce), "missing_email_verified")
        }

    @Test
    fun `rejects credential whose disclosure does not match the _sd array`() =
        runBlocking {
            val credential = buildCredentialJson(tamperFirstDisclosure = true)
            assertRejected(verifier.verify(credential, expectedNonce), "disclosure_hash_mismatch")
        }

    @Test
    fun `rejects credential with no key-binding JWT`() =
        runBlocking {
            val credential = buildCredentialJson(omitKeyBinding = true)
            assertRejected(verifier.verify(credential, expectedNonce), "missing_kb_jwt")
        }

    @Test
    fun `rejects credential whose key-binding signature is invalid`() =
        runBlocking {
            val credential = buildCredentialJson(signKbWithOverride = true)
            assertRejected(verifier.verify(credential, expectedNonce), "kb_signature_invalid")
        }

    @Test
    fun `rejects credential whose key-binding nonce does not match`() =
        runBlocking {
            val credential = buildCredentialJson(kbNonceOverride = "wrong-nonce")
            assertRejected(verifier.verify(credential, expectedNonce), "nonce_mismatch")
        }

    @Test
    fun `rejects credential whose key-binding audience does not match`() =
        runBlocking {
            val credential = buildCredentialJson(kbAudOverride = "https://attacker.example/")
            assertRejected(verifier.verify(credential, expectedNonce), "audience_mismatch")
        }

    // --- helpers -----------------------------------------------------------

    private fun assertRejected(
        result: Result<VerifiedEmailClaims>,
        expectedReason: String,
    ) {
        assertTrue(result.isFailure, "expected failure with reason $expectedReason but got success")
        val ex = result.exceptionOrNull()
        assertTrue(ex is VerificationException, "expected VerificationException, got ${ex?.javaClass?.simpleName}: ${ex?.message}")
        assertEquals(expectedReason, ex.message)
    }

    /**
     * Builds a full credential JSON envelope (`{"vp_token": {"q": [sdJwt]}}`) wrapping
     * a freshly-generated SD-JWT VC. Each named parameter lets a test tweak exactly one
     * dimension of the credential to exercise a single rejection path.
     */
    @Suppress("LongParameterList")
    private fun buildCredentialJson(
        disclosed: Map<String, JsonPrimitive> = defaultDisclosedClaims,
        issuerOverride: String = DigitalCredentialVerifier.EXPECTED_ISSUER,
        vctOverride: String = DigitalCredentialVerifier.EXPECTED_VCT,
        issuerExp: Long = fixedNow.epochSeconds + 1.hours.inWholeSeconds,
        issuerIat: Long = fixedNow.epochSeconds - 1.minutes.inWholeSeconds,
        signWithOverride: ECKey? = null,
        tamperFirstDisclosure: Boolean = false,
        omitKeyBinding: Boolean = false,
        signKbWithOverride: Boolean = false,
        kbNonceOverride: String = expectedNonce,
        kbAudOverride: String = expectedAudience,
    ): String {
        // 1. Build disclosures and their base64url-encoded hashes.
        val rawDisclosures: List<String> =
            disclosed.entries.map { (name, value) ->
                val salt = UUID.randomUUID().toString()
                val arr =
                    buildJsonArray {
                        add(salt)
                        add(name)
                        add(value)
                    }
                base64UrlNoPad(arr.toString().toByteArray(Charsets.UTF_8))
            }
        val sdHashes = rawDisclosures.map { sha256Base64UrlNoPad(it.toByteArray(Charsets.US_ASCII)) }

        // 2. Build issuer JWT payload with the disclosure hashes in `_sd`.
        val payload =
            buildJsonObject {
                put("iss", issuerOverride)
                put("vct", vctOverride)
                put("iat", issuerIat)
                put("exp", issuerExp)
                put("_sd_alg", "sha-256")
                put("_sd", buildJsonArray { sdHashes.forEach { add(it) } })
                // cnf.jwk binds the credential to the holder key.
                put(
                    "cnf",
                    buildJsonObject {
                        put("jwk", holderKey.toPublicJWK().toJSONObject().toJsonObject())
                    },
                )
            }

        // 3. Sign the issuer JWT with the issuer key (or a stranger key if overridden).
        val signingKey = signWithOverride ?: issuerKey
        val issuerJwt = signJws(payload, signingKey, signingKey.keyID)

        // 4. Optionally tamper with the first disclosure so its hash no longer matches.
        val effectiveDisclosures =
            if (tamperFirstDisclosure && rawDisclosures.isNotEmpty()) {
                val first = rawDisclosures.first()
                val tampered = first.dropLast(1) + (if (first.last() == 'A') 'B' else 'A')
                listOf(tampered) + rawDisclosures.drop(1)
            } else {
                rawDisclosures
            }

        // 5. Build the key-binding JWT — unless this scenario wants it omitted.
        val keyBindingJwt =
            if (omitKeyBinding) {
                null
            } else {
                val kbKey =
                    if (signKbWithOverride) {
                        ECKeyGenerator(Curve.P_256).keyID("attacker-kb").generate()
                    } else {
                        holderKey
                    }
                val kbPayload =
                    buildJsonObject {
                        put("nonce", kbNonceOverride)
                        put("aud", kbAudOverride)
                        put("iat", fixedNow.epochSeconds)
                    }
                signJws(kbPayload, kbKey, kid = null, jwtType = "kb+jwt")
            }

        val sdJwt =
            buildString {
                append(issuerJwt)
                effectiveDisclosures.forEach { append("~").append(it) }
                if (keyBindingJwt != null) {
                    append("~").append(keyBindingJwt)
                } else {
                    append("~")
                }
            }

        val credentialJson =
            buildJsonObject {
                put(
                    "vp_token",
                    buildJsonObject {
                        put(
                            "user_info_query",
                            buildJsonArray { add(sdJwt) },
                        )
                    },
                )
            }
        return credentialJson.toString()
    }

    private val defaultDisclosedClaims: Map<String, JsonPrimitive>
        get() =
            mapOf(
                "email" to JsonPrimitive("jane.doe@example.com"),
                "email_verified" to JsonPrimitive(true),
                "name" to JsonPrimitive("Jane Doe"),
                "given_name" to JsonPrimitive("Jane"),
                "family_name" to JsonPrimitive("Doe"),
                "picture" to JsonPrimitive("https://example.com/jane.jpg"),
                "hd" to JsonPrimitive(""),
            )

    private fun signJws(
        payload: JsonObject,
        key: ECKey,
        kid: String?,
        jwtType: String? = null,
    ): String {
        val headerBuilder = JWSHeader.Builder(JWSAlgorithm.ES256)
        if (kid != null) headerBuilder.keyID(kid)
        if (jwtType != null) headerBuilder.type(JOSEObjectType(jwtType))
        val jws = JWSObject(headerBuilder.build(), Payload(payload.toString()))
        jws.sign(ECDSASigner(key))
        return jws.serialize()
    }

    private fun flipLastCharOfFirstSegment(credentialJson: String): String {
        // The credential is JSON containing a JWS-shaped string; flip the very last
        // character of the issuer JWT's signature segment to corrupt it.
        val sdJwt = credentialJson.substringAfter("[\"").substringBefore("\"]")
        val firstSegment = sdJwt.substringBefore("~")
        val flippedFirstSegment =
            firstSegment.dropLast(1) +
                (if (firstSegment.last() == 'A') 'B' else 'A')
        val tamperedSdJwt = flippedFirstSegment + sdJwt.removePrefix(firstSegment)
        return credentialJson.replace(sdJwt, tamperedSdJwt)
    }

    private fun base64UrlNoPad(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun sha256Base64UrlNoPad(bytes: ByteArray): String = base64UrlNoPad(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** Tiny converter from Nimbus's `Map<String, Any?>` JWK JSON to a kotlinx JsonObject. */
    private fun Map<String, Any?>.toJsonObject(): JsonObject =
        buildJsonObject {
            this@toJsonObject.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }
}
