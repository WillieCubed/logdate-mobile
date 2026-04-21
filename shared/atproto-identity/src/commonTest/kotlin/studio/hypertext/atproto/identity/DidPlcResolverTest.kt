package studio.hypertext.atproto.identity

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests the [DidPlcResolver] implementation for resolving AT Protocol "did:plc"
 * identifiers.
 *
 * This suite covers the interaction with the PLC (Placeholder) directory, validating
 * URL construction, successful document retrieval, and the handling of various error
 * conditions such as network failures, malformed JSON, or mismatches between the
 * requested DID and the returned document.
 */
class DidPlcResolverTest {
    @Test
    fun buildsPlcDirectoryUrl() {
        val resolver = DidPlcResolver(mockHttpClient { _ -> HttpStatusCode.OK to """{"id":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""" })

        assertEquals(
            "https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz",
            resolver.urlFor(AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")),
        )
    }

    @Test
    fun resolvesPlcDocument(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val resolver =
                DidPlcResolver(
                    mockHttpClient { path ->
                        assertEquals("/did:plc:ewvi7nxzyoun6zhxrhs64oiz", path)
                        HttpStatusCode.OK to """{"id":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}"""
                    },
                )

            val result = resolver.resolve(did)

            assertTrue(result.isSuccess)
            assertEquals(did, result.getOrThrow().id)
        }

    @Test
    fun failsOnNonSuccessStatus(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val resolver =
                DidPlcResolver(
                    mockHttpClient { _ -> HttpStatusCode.NotFound to """{"error":"missing"}""" },
                )

            val result = resolver.resolve(did)

            assertTrue(result.isFailure)
            assertFailsWith<DidResolutionException> {
                result.getOrThrow()
            }
        }

    @Test
    fun failsOnInvalidJson(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val resolver =
                DidPlcResolver(
                    mockHttpClient { _ -> HttpStatusCode.OK to """{"id":""" },
                )

            val result = resolver.resolve(did)

            assertTrue(result.isFailure)
            assertFailsWith<DidResolutionException> {
                result.getOrThrow()
            }
        }

    @Test
    fun failsOnDidMismatch(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val resolver =
                DidPlcResolver(
                    mockHttpClient { _ -> HttpStatusCode.OK to """{"id":"did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"}""" },
                )

            val result = resolver.resolve(did)

            assertTrue(result.isFailure)
            assertFailsWith<DidDocumentMismatchException> {
                result.getOrThrow()
            }
        }
}
