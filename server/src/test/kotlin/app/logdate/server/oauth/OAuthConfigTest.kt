package app.logdate.server.oauth

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OAuthConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `metadata uses normalized issuer and default resource`() {
        val config = OAuthConfig(issuer = "https://logdate.app/")

        val metadata = config.authorizationServerMetadata()
        val protectedResource = config.protectedResourceMetadata()

        assertEquals("https://logdate.app/", config.issuer)
        assertEquals("https://logdate.app/", config.resource)
        assertEquals("https://logdate.app", config.normalizedIssuer)
        assertEquals("https://logdate.app", config.normalizedResource)
        assertEquals("https://logdate.app/oauth/authorize", config.authorizationEndpoint)
        assertEquals("https://logdate.app/oauth/token", config.tokenEndpoint)
        assertEquals("https://logdate.app/oauth/par", config.pushedAuthorizationRequestEndpoint)
        assertEquals("https://logdate.app/oauth/revoke", config.revocationEndpoint)
        assertEquals("https://logdate.app/oauth/jwks", config.jwksUri)
        assertEquals("https://logdate.app", metadata.issuer)
        assertEquals("https://logdate.app/oauth/authorize", metadata.authorization_endpoint)
        assertEquals("https://logdate.app/oauth/token", metadata.token_endpoint)
        assertEquals("https://logdate.app/oauth/par", metadata.pushed_authorization_request_endpoint)
        assertEquals("https://logdate.app/oauth/revoke", metadata.revocation_endpoint)
        assertEquals("https://logdate.app/oauth/jwks", metadata.jwks_uri)
        assertEquals(listOf("code"), metadata.response_types_supported)
        assertEquals(listOf("authorization_code", "refresh_token"), metadata.grant_types_supported)
        assertEquals(listOf("S256"), metadata.code_challenge_methods_supported)
        assertEquals(listOf("none"), metadata.token_endpoint_auth_methods_supported)
        assertEquals(listOf("ES256"), metadata.dpop_signing_alg_values_supported)
        assertEquals(listOf("atproto"), metadata.scopes_supported)
        assertTrue(metadata.client_id_metadata_document_supported)
        assertEquals("https://logdate.app", protectedResource.resource)
        assertEquals(listOf("https://logdate.app"), protectedResource.authorization_servers)
    }

    @Test
    fun `fromEnvironment uses defaults and explicit resource override`() {
        val defaultMethod =
            OAuthConfig.Companion::class.java.getDeclaredMethod(
                "fromEnvironment\$default",
                OAuthConfig.Companion::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val defaultCall = OAuthConfig.fromEnvironment(defaultIssuer = "https://default.logdate.app")
        val defaultIssuerPath =
            defaultMethod.invoke(
                null,
                OAuthConfig.Companion,
                null,
                "https://issuer.logdate.app/",
                "https://resource.logdate.app/",
                1,
                null,
            ) as OAuthConfig
        val defaulted = OAuthConfig.fromEnvironment(defaultIssuer = "https://pds.logdate.app", issuer = " ", resource = null)
        val overridden =
            OAuthConfig.fromEnvironment(
                defaultIssuer = "https://pds.logdate.app",
                issuer = "https://issuer.logdate.app/",
                resource = "https://resource.logdate.app/",
            )

        assertEquals("https://default.logdate.app", defaultCall.normalizedIssuer)
        assertEquals("https://default.logdate.app", defaultCall.normalizedResource)
        assertEquals("https://issuer.logdate.app", defaultIssuerPath.normalizedIssuer)
        assertEquals("https://resource.logdate.app", defaultIssuerPath.normalizedResource)
        assertEquals("https://pds.logdate.app", defaulted.normalizedIssuer)
        assertEquals("https://pds.logdate.app", defaulted.normalizedResource)
        assertEquals("https://issuer.logdate.app", overridden.normalizedIssuer)
        assertEquals("https://resource.logdate.app", overridden.normalizedResource)
    }

    @Test
    fun `constructor requires https issuer and resource`() {
        assertFailsWith<IllegalArgumentException> {
            OAuthConfig(issuer = "http://logdate.app")
        }
        assertFailsWith<IllegalArgumentException> {
            OAuthConfig(issuer = "https://logdate.app", resource = "http://resource.logdate.app")
        }
    }

    @Test
    fun `json web key serializer round trips`() {
        val key =
            JsonWebKey(
                kty = "EC",
                use = "sig",
                key_ops = listOf("verify"),
                alg = "ES256",
                kid = "kid-1",
                crv = "P-256",
                x = "x-value",
                y = "y-value",
            )

        val encoded = json.encodeToString(JsonWebKey.serializer(), key)
        val decoded = json.decodeFromString(JsonWebKey.serializer(), encoded)

        assertEquals(key, decoded)
    }
}
