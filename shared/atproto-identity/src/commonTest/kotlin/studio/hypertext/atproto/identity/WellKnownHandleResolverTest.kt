package studio.hypertext.atproto.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.syntax.Handle
import studio.hypertext.atproto.syntax.InvalidDidException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WellKnownHandleResolverTest {
    @Test
    fun resolvesHandleViaWellKnownEndpoint(): Unit =
        runTest {
            val resolver =
                WellKnownHandleResolver(
                    HttpClient(
                        MockEngine { request ->
                            assertEquals("/.well-known/atproto-did", request.url.fullPath)
                            respond(
                                content = "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
                            )
                        },
                    ),
                )

            val result = resolver.resolve(Handle.require("example.com"))

            assertTrue(result.isSuccess)
            assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", result.getOrThrow().toString())
        }

    @Test
    fun returnsFailureForNotFound(): Unit =
        runTest {
            val resolver =
                WellKnownHandleResolver(
                    HttpClient(
                        MockEngine {
                            respond(
                                content = "not found",
                                status = HttpStatusCode.NotFound,
                                headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
                            )
                        },
                    ),
                )

            val result = resolver.resolve(Handle.require("example.com"))

            assertTrue(result.isFailure)
        }

    @Test
    fun returnsFailureForInvalidDidBody(): Unit =
        runTest {
            val resolver =
                WellKnownHandleResolver(
                    HttpClient(
                        MockEngine {
                            respond(
                                content = "not-a-did",
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
                            )
                        },
                    ),
                )

            val result = resolver.resolve(Handle.require("example.com"))

            assertTrue(result.isFailure)
            assertFailsWith<InvalidDidException> {
                result.getOrThrow()
            }
        }
}
