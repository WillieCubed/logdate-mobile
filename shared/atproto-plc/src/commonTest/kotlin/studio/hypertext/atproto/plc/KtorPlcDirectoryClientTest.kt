package studio.hypertext.atproto.plc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for the Ktor-based implementation of [PlcDirectoryClient].
 *
 * This test suite validates the interaction with a PLC directory server (e.g., plc.directory).
 * It ensures correct handling of:
 * - URL path construction for various PLC-specific resources.
 * - Retrieval and polymorphic decoding of DID documents, operation logs, and audit logs.
 * - Large-scale data exports using newline-delimited JSON formats.
 * - Secure submission of signed operations to the directory service.
 */
class KtorPlcDirectoryClientTest {
    @Test
    fun `document log audit and export urls use plc directory paths`() {
        val client = KtorPlcDirectoryClient(httpClient = mockClient { error("unused") })
        val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")

        assertEquals("https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz", client.documentUrlFor(did))
        assertEquals("https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz/log", client.operationLogUrlFor(did))
        assertEquals("https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz/log/audit", client.auditLogUrlFor(did))
        assertEquals("https://plc.directory/did:plc:ewvi7nxzyoun6zhxrhs64oiz", client.submitUrlFor(did))
        assertEquals("https://plc.directory/export", client.exportUrl())
        assertEquals("https://plc.directory/export?after=cursor&count=25", client.exportUrl(after = "cursor", count = 25))
        assertFailsWith<IllegalArgumentException> {
            client.documentUrlFor(AtprotoDid.require("did:web:example.com"))
        }
    }

    @Test
    fun `getDocument decodes plc directory did document`() =
        kotlinx.coroutines.test.runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val client =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient { request ->
                            assertEquals("/did:plc:ewvi7nxzyoun6zhxrhs64oiz", request.url.encodedPath)
                            respond(
                                content = """{"id":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""",
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders(),
                            )
                        },
                )

            val result = client.getDocument(did)

            assertEquals(did, result.getOrThrow().id)
        }

    @Test
    fun `getDocument rejects status and id mismatches`() =
        kotlinx.coroutines.test.runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val failingClient =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient {
                            respond("{}", HttpStatusCode.NotFound, jsonHeaders())
                        },
                )
            val mismatchedClient =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient {
                            respond(
                                """{"id":"did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"}""",
                                HttpStatusCode.OK,
                                jsonHeaders(),
                            )
                        },
                )

            assertTrue(failingClient.getDocument(did).isFailure)
            assertTrue(mismatchedClient.getDocument(did).isFailure)
        }

    @Test
    fun `getOperationLog decodes operations and tombstones`() =
        kotlinx.coroutines.test.runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val client =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient { request ->
                            assertEquals("/did:plc:ewvi7nxzyoun6zhxrhs64oiz/log", request.url.encodedPath)
                            respond(
                                content =
                                    """
                                    [
                                      {
                                        "sig":"sig",
                                        "prev":null,
                                        "type":"plc_operation",
                                        "services":{"atproto_pds":{"type":"AtprotoPersonalDataServer","endpoint":"https://bsky.social"}},
                                        "alsoKnownAs":["at://atproto.com"],
                                        "rotationKeys":["did:key:zQ3shhCGUqDKjStzuDxPkTxN6ujddP4RkEKJJouJGRRkaLGbg"],
                                        "verificationMethods":{"atproto":"did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"}
                                      },
                                      {
                                        "sig":"tombstone-sig",
                                        "prev":"bafyreigenesis",
                                        "type":"plc_tombstone"
                                      }
                                    ]
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders(),
                            )
                        },
                )

            val result = client.getOperationLog(did).getOrThrow()

            assertEquals(2, result.size)
            assertIs<PlcOperation>(result.first())
            assertIs<PlcTombstone>(result.last())
            assertEquals("bafyreigenesis", (result.last() as PlcTombstone).prev)
        }

    @Test
    fun `getAuditLog decodes indexed plc audit entries`() =
        kotlinx.coroutines.test.runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val client =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient { request ->
                            assertEquals("/did:plc:ewvi7nxzyoun6zhxrhs64oiz/log/audit", request.url.encodedPath)
                            respond(
                                content =
                                    """
                                    [
                                      {
                                        "did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz",
                                        "operation":{
                                          "sig":"sig",
                                          "prev":null,
                                          "type":"plc_operation",
                                          "services":{"atproto_pds":{"type":"AtprotoPersonalDataServer","endpoint":"https://bsky.social"}},
                                          "alsoKnownAs":["at://atproto.com"],
                                          "rotationKeys":["did:key:zQ3shhCGUqDKjStzuDxPkTxN6ujddP4RkEKJJouJGRRkaLGbg"],
                                          "verificationMethods":{"atproto":"did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"}
                                        },
                                        "cid":"bafyrei123",
                                        "nullified":false,
                                        "createdAt":"2023-04-26T06:19:25.508Z"
                                      }
                                    ]
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = jsonHeaders(),
                            )
                        },
                )

            val result = client.getAuditLog(did).getOrThrow()

            assertEquals(1, result.size)
            assertEquals(did, result.single().did)
            assertEquals("bafyrei123", result.single().cid)
        }

    @Test
    fun `export decodes newline delimited indexed operations`() =
        kotlinx.coroutines.test.runTest {
            val client =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient { request ->
                            assertEquals("/export", request.url.encodedPath)
                            assertEquals("cursor", request.url.parameters["after"])
                            assertEquals("2", request.url.parameters["count"])
                            respond(
                                content =
                                    """
                                    {"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz","operation":{"sig":"sig","prev":null,"type":"plc_operation","services":{"atproto_pds":{"type":"AtprotoPersonalDataServer","endpoint":"https://bsky.social"}},"alsoKnownAs":["at://atproto.com"],"rotationKeys":["did:key:zQ3shhCGUqDKjStzuDxPkTxN6ujddP4RkEKJJouJGRRkaLGbg"],"verificationMethods":{"atproto":"did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"}},"cid":"bafyrei123","nullified":false,"createdAt":"2023-04-26T06:19:25.508Z"}
                                    {"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz","operation":{"sig":"sig-2","prev":"bafyrei123","type":"plc_tombstone"},"cid":"bafyrei456","nullified":false,"createdAt":"2023-04-27T06:19:25.508Z"}
                                    """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
                            )
                        },
                )

            val result = client.export(after = "cursor", count = 2).getOrThrow()

            assertEquals(2, result.size)
            assertIs<PlcOperation>(result.first().operation)
            assertIs<PlcTombstone>(result.last().operation)
        }

    @Test
    fun `submit posts signed json entries and rejects errors`() =
        kotlinx.coroutines.test.runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val operation =
                PlcOperation(
                    sig = "sig-value",
                    services = mapOf("atproto_pds" to PlcService("AtprotoPersonalDataServer", "https://logdate.app")),
                    alsoKnownAs = listOf("at://alice.logdate.app"),
                    rotationKeys = listOf("did:key:zQ3shhCGUqDKjStzuDxPkTxN6ujddP4RkEKJJouJGRRkaLGbg"),
                    verificationMethods = mapOf("atproto" to "did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"),
                )
            val submittedBodies = mutableListOf<String>()
            val client =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient { request ->
                            submittedBodies += readBody(request)
                            respond("", HttpStatusCode.OK, jsonHeaders())
                        },
                )
            val failingClient =
                KtorPlcDirectoryClient(
                    httpClient =
                        mockClient {
                            respond("", HttpStatusCode.BadRequest, jsonHeaders())
                        },
                )

            assertTrue(client.submit(did, operation).isSuccess)
            assertTrue(submittedBodies.single().contains("\"sig\":\"sig-value\""))
            assertTrue(submittedBodies.single().contains("\"type\":\"plc_operation\""))
            assertTrue(failingClient.submit(did, operation).isFailure)
            assertFailsWith<IllegalArgumentException> {
                failingClient.submit(did, operation.copy(sig = null)).getOrThrow()
            }
        }

    private fun mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
        }

    private fun readBody(request: HttpRequestData): String =
        when (val body = request.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            is OutgoingContent.ReadChannelContent -> error("Unexpected channel body in PLC tests")
            is OutgoingContent.WriteChannelContent -> error("Unexpected streaming body in PLC tests")
            is OutgoingContent.NoContent -> ""
            else -> body.toString()
        }

    private fun jsonHeaders() =
        headersOf(
            "Content-Type",
            ContentType.Application.Json.toString(),
        )
}
