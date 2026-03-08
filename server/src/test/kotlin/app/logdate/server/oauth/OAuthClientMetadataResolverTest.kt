package app.logdate.server.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlin.jvm.internal.DefaultConstructorMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class OAuthClientMetadataResolverTest {
    @Test
    fun `resolver validates client metadata and caches successful lookups`() =
        kotlinx.coroutines.test.runTest {
            var requests = 0
            val engine =
                MockEngine { request ->
                    requests++
                    respond(
                        content = clientMetadataJson(request.url.toString(), "https://journal-viewer.example.com/callback"),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient = HttpClient(engine),
                    clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z")),
                )

            val first = resolver.resolve("https://journal-viewer.example.com/client-metadata.json").getOrThrow()
            val second = resolver.resolve("https://journal-viewer.example.com/client-metadata.json").getOrThrow()

            assertEquals(first, second)
            assertEquals(1, requests)
            assertTrue(first.supportsRedirect("https://journal-viewer.example.com/callback"))
            assertEquals(setOf("atproto"), first.scopeSet())
        }

    @Test
    fun `resolver rejects invalid client ids and metadata documents`() =
        kotlinx.coroutines.test.runTest {
            val resolver =
                OAuthClientMetadataResolver(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = clientMetadataJson("https://other.example.com/client.json", "https://example.com/callback"),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                )

            val invalidScheme = resolver.resolve("http://example.com/client.json").exceptionOrNull()
            val mismatchedClientId = resolver.resolve("https://journal-viewer.example.com/client.json").exceptionOrNull()

            assertIs<OAuthInvalidClientException>(invalidScheme)
            assertIs<OAuthInvalidClientException>(mismatchedClientId)
        }

    @Test
    fun `resolver rejects non success responses unsupported auth methods and missing dpop binding`() =
        kotlinx.coroutines.test.runTest {
            var requestIndex = 0
            val engine =
                MockEngine {
                    requestIndex++
                    when (requestIndex) {
                        1 ->
                            respond(
                                content = """{"error":"missing"}""",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        2 ->
                            respond(
                                content =
                                    clientMetadataJson(
                                        clientId = "https://viewer.example.com/client.json",
                                        redirectUri = "https://viewer.example.com/callback",
                                        tokenEndpointAuthMethod = "private_key_jwt",
                                    ),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        else ->
                            respond(
                                content =
                                    clientMetadataJson(
                                        clientId = "https://viewer.example.com/client.json",
                                        redirectUri = "https://viewer.example.com/callback",
                                        dpopBoundAccessTokens = false,
                                    ),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )
                    }
                }
            val resolver = OAuthClientMetadataResolver(httpClient = HttpClient(engine))

            val missing = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()
            val unsupportedAuthMethod = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()
            val missingDpopBinding = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()

            assertIs<OAuthInvalidClientException>(missing)
            assertIs<OAuthInvalidClientException>(unsupportedAuthMethod)
            assertIs<OAuthInvalidClientException>(missingDpopBinding)
        }

    @Test
    fun `resolver accepts loopback http clients and rejects missing redirects response types and grant types`() =
        kotlinx.coroutines.test.runTest {
            var requestIndex = 0
            val engine =
                MockEngine {
                    requestIndex++
                    when (requestIndex) {
                        1 ->
                            respond(
                                content =
                                    """
                                    {
                                      "client_id": "http://localhost:3000/client.json",
                                      "redirect_uris": ["http://localhost:3000/callback"],
                                      "grant_types": ["authorization_code", "refresh_token"],
                                      "response_types": ["code"],
                                      "scope": "   ",
                                      "token_endpoint_auth_method": "none",
                                      "dpop_bound_access_tokens": true,
                                      "client_name": "   "
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        2 ->
                            respond(
                                content =
                                    """
                                    {
                                      "client_id": "https://viewer.example.com/client.json",
                                      "redirect_uris": [],
                                      "grant_types": ["authorization_code", "refresh_token"],
                                      "response_types": ["code"],
                                      "token_endpoint_auth_method": "none",
                                      "dpop_bound_access_tokens": true
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        3 ->
                            respond(
                                content =
                                    """
                                    {
                                      "client_id": "https://viewer.example.com/client.json",
                                      "redirect_uris": ["https://viewer.example.com/callback"],
                                      "grant_types": ["authorization_code", "refresh_token"],
                                      "response_types": [],
                                      "token_endpoint_auth_method": "none",
                                      "dpop_bound_access_tokens": true
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        4 ->
                            respond(
                                content =
                                    """
                                    {
                                      "client_id": "https://viewer.example.com/client.json",
                                      "redirect_uris": ["https://viewer.example.com/callback"],
                                      "grant_types": ["refresh_token"],
                                      "response_types": ["code"],
                                      "token_endpoint_auth_method": "none",
                                      "dpop_bound_access_tokens": true
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )

                        else ->
                            respond(
                                content =
                                    """
                                    {
                                      "client_id": "https://viewer.example.com/client.json",
                                      "redirect_uris": ["https://viewer.example.com/callback"],
                                      "grant_types": ["authorization_code"],
                                      "response_types": ["code"],
                                      "token_endpoint_auth_method": "none",
                                      "dpop_bound_access_tokens": true
                                    }
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )
                    }
                }
            val resolver = OAuthClientMetadataResolver(httpClient = HttpClient(engine))

            val loopback = resolver.resolve("http://localhost:3000/client.json").getOrThrow()
            val missingRedirects = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()
            val missingResponseType = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()
            val missingAuthorizationGrant = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()
            val missingGrantTypes = resolver.resolve("https://viewer.example.com/client.json").exceptionOrNull()

            assertEquals(null, loopback.scope)
            assertEquals(null, loopback.client_name)
            assertIs<OAuthInvalidClientException>(missingRedirects)
            assertIs<OAuthInvalidClientException>(missingResponseType)
            assertIs<OAuthInvalidClientException>(missingAuthorizationGrant)
            assertIs<OAuthInvalidClientException>(missingGrantTypes)
        }

    @Test
    fun `client metadata model serializes and exposes helper methods`() {
        val metadata =
            OAuthClientMetadata(
                client_id = "https://viewer.example.com/client.json",
                redirect_uris = listOf("https://viewer.example.com/callback"),
                grant_types = listOf("authorization_code", "refresh_token"),
                response_types = listOf("code"),
                scope = "atproto profile",
                token_endpoint_auth_method = "none",
                dpop_bound_access_tokens = true,
                client_name = "Viewer",
            )
        val encoded = Json.encodeToString(OAuthClientMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(OAuthClientMetadata.serializer(), encoded)
        val defaulted =
            Json.decodeFromString(
                OAuthClientMetadata.serializer(),
                """
                {
                  "client_id": "https://viewer.example.com/client.json",
                  "redirect_uris": ["https://viewer.example.com/callback"]
                }
                """.trimIndent(),
            )

        assertEquals(metadata, decoded)
        assertEquals("https://viewer.example.com/client.json", decoded.client_id)
        assertEquals(listOf("https://viewer.example.com/callback"), decoded.redirect_uris)
        assertEquals(listOf("authorization_code", "refresh_token"), decoded.grant_types)
        assertEquals(listOf("code"), decoded.response_types)
        assertEquals("atproto profile", decoded.scope)
        assertTrue(decoded.supportsRedirect("https://viewer.example.com/callback"))
        assertEquals(setOf("atproto", "profile"), decoded.scopeSet())
        assertEquals("none", decoded.token_endpoint_auth_method)
        assertEquals("Viewer", decoded.client_name)
        assertTrue(defaulted.dpop_bound_access_tokens)
        assertEquals(listOf("authorization_code", "refresh_token"), defaulted.grant_types)
        assertEquals(listOf("code"), defaulted.response_types)
        assertEquals(defaulted, defaulted.copy())
    }

    @Test
    fun `resolver rejects blank and malformed client ids`() =
        kotlinx.coroutines.test.runTest {
            val resolver = OAuthClientMetadataResolver(httpClient = HttpClient(MockEngine { error("unused") }))

            assertIs<IllegalArgumentException>(resolver.resolve("   ").exceptionOrNull())
            assertIs<OAuthInvalidClientException>(resolver.resolve("%%%").exceptionOrNull())
        }

    @Test
    fun `client metadata synthetic default constructor applies kotlin defaults`() {
        val constructor =
            OAuthClientMetadata::class.java.getDeclaredConstructor(
                String::class.java,
                List::class.java,
                List::class.java,
                List::class.java,
                String::class.java,
                String::class.java,
                java.lang.Boolean.TYPE,
                String::class.java,
                Integer.TYPE,
                DefaultConstructorMarker::class.java,
            )
        constructor.isAccessible = true

        val instance =
            constructor.newInstance(
                "https://viewer.example.com/client.json",
                listOf("https://viewer.example.com/callback"),
                emptyList<String>(),
                emptyList<String>(),
                "ignored",
                "ignored",
                false,
                "ignored",
                0b11111100,
                null,
            )

        assertEquals(listOf("authorization_code", "refresh_token"), instance.grant_types)
        assertEquals(listOf("code"), instance.response_types)
        assertEquals(null, instance.scope)
        assertEquals("none", instance.token_endpoint_auth_method)
        assertTrue(instance.dpop_bound_access_tokens)
        assertEquals(null, instance.client_name)
    }
}
