package app.logdate.atproto.identity

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
