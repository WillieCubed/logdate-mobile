package studio.hypertext.atproto.identity

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DidWebResolverTest {
    @Test
    fun buildsWellKnownUrl() {
        val resolver = DidWebResolver(mockHttpClient { _ -> HttpStatusCode.OK to """{"id":"did:web:example.com"}""" })

        assertEquals("https://example.com/.well-known/did.json", resolver.urlFor(AtprotoDid.require("did:web:example.com")))
    }

    @Test
    fun decodesPortFromDidWebIdentifier() {
        val resolver = DidWebResolver(mockHttpClient { _ -> HttpStatusCode.OK to """{"id":"did:web:example.com%3A8443"}""" })

        assertEquals(
            "https://example.com:8443/.well-known/did.json",
            resolver.urlFor(AtprotoDid.require("did:web:example.com%3A8443")),
        )
    }

    @Test
    fun resolvesDocument(): Unit =
        runTest {
            val resolver =
                DidWebResolver(
                    mockHttpClient { path ->
                        assertEquals("/.well-known/did.json", path)
                        HttpStatusCode.OK to """{"id":"did:web:example.com"}"""
                    },
                )

            val result = resolver.resolve(AtprotoDid.require("did:web:example.com"))

            assertTrue(result.isSuccess)
            assertEquals("did:web:example.com", result.getOrThrow().id.toString())
        }

    @Test
    fun failsOnDidMismatch(): Unit =
        runTest {
            val resolver =
                DidWebResolver(
                    mockHttpClient { _ -> HttpStatusCode.OK to """{"id":"did:web:not-example.com"}""" },
                )

            val result = resolver.resolve(AtprotoDid.require("did:web:example.com"))

            assertTrue(result.isFailure)
            assertFailsWith<DidDocumentMismatchException> {
                result.getOrThrow()
            }
        }

    @Test
    fun failsOnNonSuccessStatus(): Unit =
        runTest {
            val resolver =
                DidWebResolver(
                    mockHttpClient { _ -> HttpStatusCode.NotFound to """{"error":"missing"}""" },
                )

            val result = resolver.resolve(AtprotoDid.require("did:web:example.com"))

            assertTrue(result.isFailure)
            assertFailsWith<DidResolutionException> {
                result.getOrThrow()
            }
        }

    @Test
    fun failsOnInvalidJson(): Unit =
        runTest {
            val resolver =
                DidWebResolver(
                    mockHttpClient { _ -> HttpStatusCode.OK to """{"id":""" },
                )

            val result = resolver.resolve(AtprotoDid.require("did:web:example.com"))

            assertTrue(result.isFailure)
            assertFailsWith<DidResolutionException> {
                result.getOrThrow()
            }
        }
}
