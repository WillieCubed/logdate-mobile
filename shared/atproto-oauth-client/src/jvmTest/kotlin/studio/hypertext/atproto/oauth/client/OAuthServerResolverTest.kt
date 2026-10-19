package studio.hypertext.atproto.oauth.client

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OAuthServerResolverTest {
    private val atmosphere = MockAtmosphere()
    private val resolver = OAuthServerResolver(atmosphere.identityResolver, atmosphere.httpClient)

    @Test
    fun resolvesAHandleThroughItsDataServerToTheEntryway() =
        runTest {
            val resolved = resolver.resolve("Alice.Example.com")

            assertEquals(atmosphere.alice.did, resolved.identity!!.did.toString())
            assertEquals("alice.example.com", resolved.identity.handle!!.normalized)
            assertEquals(atmosphere.pdsUrl, resolved.identity.pdsUrl)
            assertEquals(atmosphere.issuer, resolved.metadata.issuer)
        }

    @Test
    fun rejectsAHandleTheDidDocumentDoesNotClaim() =
        runTest {
            atmosphere.accounts +=
                MockAccount("did:plc:imposterrrrrrrrrrrrrrrrr", "imposter.example.com", atmosphere.pdsUrl, claimsHandle = false)

            assertFailsWith<OAuthHandleMismatchException> { resolver.resolve("imposter.example.com") }
        }

    @Test
    fun reportsAHandleThatDoesNotResolve() =
        runTest {
            val error = assertFailsWith<OAuthIdentityResolutionException> { resolver.resolve("nobody.example.com") }

            assertEquals("nobody.example.com", error.input)
        }

    @Test
    fun reportsInputThatIsNeitherAHandleNorADid() =
        runTest {
            assertFailsWith<OAuthIdentityResolutionException> { resolver.resolve("not a handle") }
            assertFailsWith<OAuthIdentityResolutionException> {
                resolver.resolve("did:key:zexample")
            }
        }

    @Test
    fun reportsADataServerThatIsNotHttps() =
        runTest {
            atmosphere.accounts += MockAccount("did:plc:insecureeeeeeeeeeeeeeeee", "insecure.example.com", "http://pds.example.com")

            assertFailsWith<OAuthPdsNotFoundException> { resolver.resolve("insecure.example.com") }
        }

    @Test
    fun rejectsAServerWithoutClientMetadataDocuments() =
        runTest {
            atmosphere.authorizationServerMetadataOverride = { JsonObject(it - "client_id_metadata_document_supported") }

            val error = assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }

            assertTrue(error.message!!.contains("client_id_metadata_document_supported"))
        }

    @Test
    fun rejectsAServerThatDoesNotSendTheIssuerInCallbacks() =
        runTest {
            atmosphere.authorizationServerMetadataOverride = { JsonObject(it - "authorization_response_iss_parameter_supported") }

            val error = assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }

            assertTrue(error.message!!.contains("authorization_response_iss_parameter_supported"))
        }

    @Test
    fun rejectsAServerThatDoesNotRequirePushedRequests() =
        runTest {
            atmosphere.authorizationServerMetadataOverride = {
                JsonObject(it + ("require_pushed_authorization_requests" to JsonPrimitive(false)))
            }

            val error = assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }

            assertTrue(error.message!!.contains("require_pushed_authorization_requests"))
        }

    @Test
    fun requiresEs256ClientAssertionsAndRejectsUnsignedOnes() =
        runTest {
            val cases =
                mapOf(
                    "token_endpoint_auth_signing_alg_values_supported is missing" to { metadata: JsonObject ->
                        JsonObject(metadata - "token_endpoint_auth_signing_alg_values_supported")
                    },
                    "client assertion algorithm ES256" to { metadata: JsonObject ->
                        JsonObject(metadata + ("token_endpoint_auth_signing_alg_values_supported" to strings("RS256")))
                    },
                    "allows \"none\"" to { metadata: JsonObject ->
                        JsonObject(metadata + ("token_endpoint_auth_signing_alg_values_supported" to strings("ES256", "none")))
                    },
                )
            cases.forEach { (expected, override) ->
                atmosphere.authorizationServerMetadataOverride = override

                val error = assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }

                assertTrue(error.message!!.contains(expected), "expected \"$expected\" in ${error.message}")
            }
        }

    @Test
    fun rejectsEndpointsOffTheIssuersOriginOrWithoutHttps() =
        runTest {
            val cases =
                listOf(
                    "token_endpoint is not at the issuer's origin" to ("token_endpoint" to "https://collector.example.org/token"),
                    "token_endpoint is not at the issuer's origin" to ("token_endpoint" to "https://auth.example.com:8443/oauth/token"),
                    "authorization_endpoint must use https://" to ("authorization_endpoint" to "http://auth.example.com/oauth/authorize"),
                    "pushed_authorization_request_endpoint is not a valid URL" to ("pushed_authorization_request_endpoint" to "/oauth/par"),
                    "revocation_endpoint is not at the issuer's origin" to
                        ("revocation_endpoint" to "https://auth.example.com.evil.net/revoke"),
                )
            cases.forEach { (expected, endpoint) ->
                atmosphere.authorizationServerMetadataOverride = { JsonObject(it + (endpoint.first to JsonPrimitive(endpoint.second))) }

                val error = assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }

                assertTrue(error.message!!.contains(expected), "expected \"$expected\" in ${error.message}")
            }
        }

    @Test
    fun rejectsMetadataForAnotherIssuer() =
        runTest {
            atmosphere.authorizationServerMetadataOverride = { JsonObject(it + ("issuer" to JsonPrimitive("https://evil.example.com"))) }

            assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }
        }

    @Test
    fun rejectsAnIssuerThatDoesNotProtectTheDataServer() =
        runTest {
            atmosphere.authorizationServerMetadataOverride = {
                JsonObject(it + ("protected_resources" to buildJsonArray { add(JsonPrimitive("https://unrelated.example.com")) }))
            }

            assertFailsWith<OAuthServerMetadataException> { resolver.resolve("alice.example.com") }
        }

    @Test
    fun rejectsPlainHttpServersUnlessAllowed() =
        runTest {
            assertFailsWith<OAuthServerMetadataException> { resolver.resolve("http://pds.example.com") }
        }

    @Test
    fun resolvesAServerUrlToItsAuthorizationServer() =
        runTest {
            val resolved = resolver.resolve("https://pds.example.com/some/path")

            assertEquals(null, resolved.identity)
            assertEquals(atmosphere.issuer, resolved.metadata.issuer)
        }

    @Test
    fun resolvesAnEntrywayUrlDirectly() =
        runTest {
            val resolved = resolver.resolve(atmosphere.issuer)

            assertEquals(atmosphere.issuer, resolved.metadata.issuer)
        }

    private fun strings(vararg values: String) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
}
