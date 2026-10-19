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
}
