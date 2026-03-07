package studio.hypertext.atproto.xrpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorXrpcClientTest {
    @Test
    fun queryBuildsGetRequestAndDecodesJson(): Unit =
        runTest {
            val client =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine { request ->
                                assertEquals(HttpMethod.Get, request.method)
                                assertEquals("/xrpc/com.atproto.identity.resolveHandle", request.url.encodedPath)
                                assertEquals("alice.test", request.url.parameters["handle"])
                                respond(
                                    content = """{"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val response: ResolveHandleResponse =
                client.query(Nsid.require("com.atproto.identity.resolveHandle")) {
                    queryParameter("handle", "alice.test")
                }

            assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", response.did)
        }

    @Test
    fun procedureAddsBearerAuthorization(): Unit =
        runTest {
            val client =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine { request ->
                                assertEquals(HttpMethod.Post, request.method)
                                assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                                assertEquals("header-value", request.headers["X-Test"])
                                respond(
                                    content = """{"ok":true}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val response: ProcedureResponse =
                client.procedure(
                    nsid = Nsid.require("com.example.rpc.createThing"),
                    body = CreateThingRequest("hello"),
                ) {
                    auth = BearerToken("token-123")
                    header("X-Test", "header-value")
                }

            assertEquals(true, response.ok)
        }

    @Test
    fun mapsProtocolErrors(): Unit =
        runTest {
            val client =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = """{"error":"InvalidRequest","message":"missing handle"}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val exception =
                assertFailsWith<XrpcProtocolException> {
                    client.query<ResolveHandleResponse>(Nsid.require("com.atproto.identity.resolveHandle"))
                }

            assertEquals(400, exception.statusCode)
            assertEquals("InvalidRequest", exception.error)
            assertEquals("missing handle", exception.errorMessage)
        }

    @Test
    fun mapsTransportSerializationAndPlainTextProtocolFailures(): Unit =
        runTest {
            val transportClient =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                error("socket down")
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val transportException =
                assertFailsWith<XrpcTransportException> {
                    transportClient.query<ResolveHandleResponse>(Nsid.require("com.atproto.identity.resolveHandle"))
                }
            assertEquals("XRPC request failed", transportException.message)

            val serializationClient =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = """{"missingDid":true}""",
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val serializationException =
                assertFailsWith<XrpcSerializationException> {
                    serializationClient.query<ResolveHandleResponse>(Nsid.require("com.atproto.identity.resolveHandle"))
                }
            assertEquals("Failed to decode XRPC response", serializationException.message)

            val protocolClient =
                KtorXrpcClient(
                    httpClient =
                        HttpClient(
                            MockEngine {
                                respond(
                                    content = "Unauthorized",
                                    status = HttpStatusCode.Unauthorized,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                                )
                            },
                        ),
                    baseUrl = "https://example.com",
                )

            val protocolException =
                assertFailsWith<XrpcProtocolException> {
                    protocolClient.query<ResolveHandleResponse>(Nsid.require("com.atproto.identity.resolveHandle"))
                }

            assertEquals(401, protocolException.statusCode)
            assertEquals(null, protocolException.error)
            assertEquals(null, protocolException.errorMessage)
        }
}

@Serializable
private data class ResolveHandleResponse(
    val did: String,
)

@Serializable
private data class CreateThingRequest(
    val title: String,
)

@Serializable
private data class ProcedureResponse(
    val ok: Boolean,
)
