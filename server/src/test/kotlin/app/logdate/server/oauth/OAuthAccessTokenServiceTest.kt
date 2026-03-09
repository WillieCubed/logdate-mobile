package app.logdate.server.oauth

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.pds.OAuthTokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class OAuthAccessTokenServiceTest {
    @Test
    fun `issued access tokens validate against the configured issuer resource and key`() {
        val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
        val keyService = OAuthKeyService()
        val service =
            OAuthAccessTokenService(
                config = OAuthConfig(issuer = "https://logdate.app", resource = "https://pds.logdate.app"),
                keyService = keyService,
                clock = clock,
            )

        val issued =
            service.issueAccessToken(
                subjectDid = "did:plc:alice123",
                clientId = "https://viewer.example.com/client.json",
                scope = "atproto",
                keyThumbprint = "thumbprint",
            )

        val validated = service.validateAccessToken(issued.token)

        assertEquals("did:plc:alice123", validated?.subjectDid)
        assertEquals("https://viewer.example.com/client.json", validated?.clientId)
        assertEquals("thumbprint", validated?.keyThumbprint)
        assertTrue(issued.expiresInSeconds > 0)
    }

    @Test
    fun `access token validation rejects malformed tampered expired and mismatched tokens`() {
        val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
        val config = OAuthConfig(issuer = "https://logdate.app", resource = "https://pds.logdate.app")
        val keyService = OAuthKeyService()
        val service = OAuthAccessTokenService(config = config, keyService = keyService, clock = clock)
        val issued = service.issueAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "thumbprint")

        assertNull(service.validateAccessToken("bad"))
        assertNull(service.validateAccessToken(tamperJwtPayload(issued.token)))

        val differentKeyService = OAuthKeyService()
        val mismatchedKidService = OAuthAccessTokenService(config = config, keyService = differentKeyService, clock = clock)
        assertNull(mismatchedKidService.validateAccessToken(issued.token))

        val wrongAudienceService =
            OAuthAccessTokenService(
                config = OAuthConfig(issuer = "https://logdate.app", resource = "https://other.logdate.app"),
                keyService = keyService,
                clock = clock,
            )
        assertNull(wrongAudienceService.validateAccessToken(issued.token))

        clock.nowValue += 2.hours
        assertNull(service.validateAccessToken(issued.token))
    }

    @Test
    fun `access token models expose getters copies and serializers`() {
        val confirmation = instantiatePrivate("app.logdate.server.oauth.OAuthTokenConfirmation", "thumbprint")
        val claims =
            instantiatePrivate(
                "app.logdate.server.oauth.OAuthAccessTokenClaims",
                "https://logdate.app",
                "did:plc:alice123",
                "https://pds.logdate.app",
                1_741_391_200L,
                1_741_391_100L,
                "jwt-id",
                "atproto",
                "https://viewer.example.com/client.json",
                confirmation,
            )
        val claimsSerializer = serializerFor(claims.javaClass)
        val confirmationSerializer = serializerFor(confirmation.javaClass)
        val encodedClaims = Json.encodeToString(claimsSerializer, claims)
        val decodedClaims = Json.decodeFromString(claimsSerializer, encodedClaims)
        val encodedConfirmation = Json.encodeToString(confirmationSerializer, confirmation)
        val decodedConfirmation = Json.decodeFromString(confirmationSerializer, encodedConfirmation)
        val issued = IssuedOAuthAccessToken("token", 3600, "did:plc:alice123")
        val validated = ValidatedOAuthAccessToken("did:plc:alice123", "https://viewer.example.com/client.json", "atproto", "thumbprint")
        val token = OAuthTokenResponse("access", "DPoP", 3600, "refresh", "did:plc:alice123", "atproto")

        assertEquals("thumbprint", invokeGetter(confirmation, "getJkt"))
        assertEquals("https://logdate.app", invokeGetter(claims, "getIss"))
        assertEquals("did:plc:alice123", invokeGetter(claims, "getSub"))
        assertEquals("https://pds.logdate.app", invokeGetter(claims, "getAud"))
        assertEquals(1_741_391_200L, invokeGetter(claims, "getExp"))
        assertEquals(1_741_391_100L, invokeGetter(claims, "getIat"))
        assertEquals("jwt-id", invokeGetter(claims, "getJti"))
        assertEquals("atproto", invokeGetter(claims, "getScope"))
        assertEquals("https://viewer.example.com/client.json", invokeGetter(claims, "getClientId"))
        assertEquals(confirmation, invokeGetter(claims, "getCnf"))
        assertEquals(claims.toString(), decodedClaims.toString())
        assertEquals(confirmation.toString(), decodedConfirmation.toString())
        assertEquals("token", issued.token)
        assertEquals(3600, issued.copy().expiresInSeconds)
        assertEquals("did:plc:alice123", issued.subjectDid)
        assertEquals("did:plc:alice123", validated.subjectDid)
        assertEquals("https://viewer.example.com/client.json", validated.clientId)
        assertEquals("atproto", validated.scope)
        assertEquals("thumbprint", validated.keyThumbprint)
        assertTrue(validated.toString().contains("thumbprint"))
        assertEquals("access", token.access_token)
        assertEquals(3600, token.expires_in)
        assertEquals("DPoP", token.token_type)
        assertEquals("refresh", token.refresh_token)
        assertEquals("did:plc:alice123", token.sub)
        assertEquals("atproto", token.scope)
        assertEquals(token, token.copy())
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializerFor(clazz: Class<*>): KSerializer<Any> =
        clazz
            .getDeclaredField("Companion")
            .apply { isAccessible = true }
            .get(null)
            .let { companion ->
                companion.javaClass
                    .getDeclaredMethod("serializer")
                    .apply { isAccessible = true }
                    .invoke(companion)
            } as KSerializer<Any>

    private fun instantiatePrivate(
        className: String,
        vararg args: Any?,
    ): Any {
        val clazz = Class.forName(className)
        val constructor =
            clazz.declaredConstructors.single { candidate ->
                val parameterTypes = candidate.parameterTypes
                parameterTypes.size == args.size &&
                    parameterTypes.withIndex().all { (index, type) ->
                        val value = args[index]
                        value == null || parameterMatches(type, value)
                    }
            }
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    private fun invokeGetter(
        target: Any,
        methodName: String,
    ): Any? =
        target.javaClass
            .getDeclaredMethod(methodName)
            .apply { isAccessible = true }
            .invoke(target)

    private fun parameterMatches(
        type: Class<*>,
        value: Any,
    ): Boolean =
        when {
            type.isPrimitive && type == java.lang.Long.TYPE -> value is Long
            else -> type.isAssignableFrom(value.javaClass)
        }
}
