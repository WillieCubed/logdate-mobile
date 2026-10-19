package studio.hypertext.atproto.oauth.client

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OAuthProtocolPiecesTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    @Test
    fun pkceChallengeMatchesTheRfc7636Example() {
        // Published test vector from RFC 7636 Appendix B, not a secret.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", // ggignore
            JvmOAuthCrypto.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"), // ggignore
        )
    }

    @Test
    fun htuDropsTheQueryAndFragment() {
        assertEquals("https://pds.example.com/xrpc/a.b.c", dpopHtu("https://pds.example.com/xrpc/a.b.c?x=1#y"))
        assertEquals("https://pds.example.com/xrpc/a.b.c", dpopHtu("https://pds.example.com/xrpc/a.b.c#y?x"))
        assertEquals("https://pds.example.com/", dpopHtu("https://pds.example.com/"))
    }

    @Test
    fun dpopProofsFollowRfc9449() {
        val key = JvmOAuthCrypto.generateEs256Key()

        val proof =
            JvmOAuthCrypto.createDpopProof(
                dpopKey = key,
                htm = "GET",
                htu = "https://pds.example.com/xrpc/com.atproto.server.getSession",
                nonce = "server-nonce",
                accessToken = "access-token",
                now = now,
            )

        val jwt = SignedJWT.parse(proof)
        assertEquals("dpop+jwt", jwt.header.type.type)
        assertEquals(JWSAlgorithm.ES256, jwt.header.algorithm)
        val embedded = jwt.header.jwk as ECKey
        assertFalse(embedded.isPrivate)
        assertNull(embedded.keyID)
        assertTrue(jwt.verify(ECDSAVerifier(embedded)))
        val claims = jwt.jwtClaimsSet
        assertEquals("GET", claims.getStringClaim("htm"))
        assertEquals("https://pds.example.com/xrpc/com.atproto.server.getSession", claims.getStringClaim("htu"))
        assertEquals("server-nonce", claims.getStringClaim("nonce"))
        assertEquals(now.epochSeconds, claims.issueTime.toInstant().epochSecond)
        assertEquals(sha256Url("access-token"), claims.getStringClaim("ath"))
        assertTrue(claims.jwtid.length >= 16)
    }

    @Test
    fun dpopProofsForTheAuthorizationServerHaveNoAccessTokenHash() {
        val proof =
            JvmOAuthCrypto.createDpopProof(
                JvmOAuthCrypto.generateEs256Key(),
                "POST",
                "https://auth.example.com/oauth/token",
                null,
                null,
                now,
            )

        val claims = SignedJWT.parse(proof).jwtClaimsSet
        assertNull(claims.getClaim("ath"))
        assertNull(claims.getClaim("nonce"))
    }

    @Test
    fun clientAssertionsFollowRfc7523AndTheAtprotoProfile() {
        val clientKey = JvmOAuthCrypto.generateClientKey("key-7")

        val assertion = JvmOAuthCrypto.createClientAssertion(clientKey, CLIENT_ID, "https://auth.example.com", now)

        val jwt = SignedJWT.parse(assertion)
        assertEquals(JWSAlgorithm.ES256, jwt.header.algorithm)
        assertEquals("key-7", jwt.header.keyID)
        assertTrue(jwt.verify(ECDSAVerifier(ECKey.parse(Json.encodeToString(JsonWebKey.serializer(), clientKey.publicJwk)))))
        val claims = jwt.jwtClaimsSet
        assertEquals(CLIENT_ID, claims.issuer)
        assertEquals(CLIENT_ID, claims.subject)
        assertEquals(listOf("https://auth.example.com"), claims.audience)
        assertEquals(now.epochSeconds, claims.issueTime.toInstant().epochSecond)
        assertEquals(now.epochSeconds + 60, claims.expirationTime.toInstant().epochSecond)
        assertTrue(claims.jwtid.isNotEmpty())
    }

    @Test
    fun importsAPkcs8ClientKey() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = generator.generateKeyPair()

        val jwk = JvmOAuthCrypto.privateJwkFromPkcs8(pair.private.encoded, "imported")

        val nimbusKey = ECKey.parse(Json.encodeToString(JsonWebKey.serializer(), jwk))
        assertEquals(pair.public, nimbusKey.toECPublicKey())
        val signature = JvmOAuthCrypto.signEs256(jwk, "payload".encodeToByteArray())
        assertEquals(64, signature.size)
        val jws = SignedJWT.parse(JvmOAuthCrypto.signJwt(jwk, buildJsonObject { put("alg", "ES256") }, JsonObject(emptyMap())))
        assertTrue(jws.verify(ECDSAVerifier(nimbusKey.toPublicJWK())))
    }

    @Test
    fun clientMetadataDocumentMatchesTheSpec() {
        val harness = OAuthTestHarness()

        val document = Json.parseToJsonElement(harness.client.clientMetadataJson()).jsonObject

        assertEquals(OAuthTestHarness.CLIENT_ID, document["client_id"]!!.jsonPrimitive.content)
        assertEquals("web", document["application_type"]!!.jsonPrimitive.content)
        assertEquals(listOf("authorization_code", "refresh_token"), document["grant_types"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("code"), document["response_types"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("private_key_jwt", document["token_endpoint_auth_method"]!!.jsonPrimitive.content)
        assertEquals("ES256", document["token_endpoint_auth_signing_alg"]!!.jsonPrimitive.content)
        assertEquals("true", document["dpop_bound_access_tokens"]!!.jsonPrimitive.content)
        assertEquals("atproto transition:generic", document["scope"]!!.jsonPrimitive.content)
        val key =
            document["jwks"]!!
                .jsonObject["keys"]!!
                .jsonArray
                .single()
                .jsonObject
        assertEquals("client-key-1", key["kid"]!!.jsonPrimitive.content)
        assertEquals("sig", key["use"]!!.jsonPrimitive.content)
        assertFalse("d" in key, "the published key set must not contain private key material")
        assertFalse("jwks_uri" in document)
    }

    @Test
    fun configurationRejectsValuesTheSpecForbids() {
        val key = JvmOAuthCrypto.generateClientKey("k")
        val valid = OAuthClientConfig(clientId = CLIENT_ID, redirectUris = listOf("https://app.example.org/cb"), keys = listOf(key))

        assertFailsWith<IllegalArgumentException> { valid.copy(clientId = "http://app.example.org/client.json") }
        assertFailsWith<IllegalArgumentException> { valid.copy(clientId = "https://app.example.org:8443/client.json") }
        assertFailsWith<IllegalArgumentException> { valid.copy(scope = "transition:generic") }
        assertFailsWith<IllegalArgumentException> { valid.copy(keys = emptyList()) }
        assertFailsWith<IllegalArgumentException> { valid.copy(redirectUris = listOf("http://app.example.org/cb")) }
        assertFailsWith<IllegalArgumentException> { valid.copy(clientUri = "https://elsewhere.example.com") }
        assertFailsWith<IllegalArgumentException> { OAuthClientKey(key.privateJwk.copy(kid = null)) }
        assertFailsWith<IllegalArgumentException> { OAuthClientKey(key.publicJwk) }
    }

    @Test
    fun keysAndSessionsNeverPrintSecrets() {
        val key = JvmOAuthCrypto.generateClientKey("k")
        val secret = requireNotNull(key.privateJwk.d)
        val session =
            OAuthStoredSession(
                did = AtprotoDid.require("did:plc:aliceaaaaaaaaaaaaaaaaaaa"),
                issuer = "https://auth.example.com",
                pdsUrl = "https://pds.example.com",
                scope = "atproto",
                accessToken = "secret-access-token",
                refreshToken = "secret-refresh-token",
                expiresAt = now,
                dpopKey = key.privateJwk,
                clientKeyId = "k",
            )

        assertFalse(secret in key.privateJwk.toString())
        assertFalse(secret in key.toString())
        assertFalse("secret" in session.toString())
        assertFalse(secret in session.toString())
    }

    @Test
    fun theMockRejectsAClientAssertionSignedWithAnUnpublishedKey() =
        runTest {
            val harness = OAuthTestHarness()
            val impostor =
                OAuthTestHarness(atmosphere = harness.atmosphere, keys = listOf(JvmOAuthCrypto.generateClientKey("client-key-1")))
            // The server knows the impostor's metadata only through the real client's document.
            harness.atmosphere.clientMetadataJson = harness.client.clientMetadataJson()

            val error = assertFailsWith<OAuthServerResponseException> { impostor.client.authorize("alice.example.com") }

            assertEquals("invalid_client", error.error)
            assertEquals("pushed_authorization_request", error.endpoint)
        }

    private fun sha256Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private companion object {
        const val CLIENT_ID = "https://app.example.org/oauth-client-metadata.json"
    }
}
