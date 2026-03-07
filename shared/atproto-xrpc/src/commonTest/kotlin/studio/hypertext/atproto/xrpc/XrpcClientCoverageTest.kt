package studio.hypertext.atproto.xrpc

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class XrpcClientCoverageTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun interfaceHelpersAndBuilderStateAreCovered(): Unit =
        runTest {
            val client: XrpcClient = FakeXrpcClient()
            val nsid = Nsid.require("com.example.rpc.echo")

            val rawQuery =
                client.queryRaw(nsid) {
                    queryParameter("limit", "10")
                }
            assertEquals("""{"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""", rawQuery)
            assertEquals("""{"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""", client.queryRaw(nsid))

            val typedQuery: CoverageQueryResponse =
                client.query(nsid) {
                    header("X-Test", "query")
                }
            assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", typedQuery.did)
            assertEquals(
                "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
                client.query(nsid, CoverageQueryResponse.serializer()).did,
            )
            assertEquals("did:plc:ewvi7nxzyoun6zhxrhs64oiz", client.query<CoverageQueryResponse>(nsid).did)

            val rawProcedure =
                client.procedureRaw(nsid, """{"title":"hello"}""") {
                    auth = BearerToken("token-123")
                }
            assertEquals("""{"ok":true}""", rawProcedure)
            assertEquals("""{"ok":true}""", client.procedureRaw(nsid))

            val typedProcedure: CoverageProcedureResponse =
                client.procedure(nsid, CoverageProcedureRequest("hello")) {
                    auth = BearerToken("token-123")
                    header("X-Test", "procedure")
                }
            assertEquals(true, typedProcedure.ok)
            assertEquals(
                true,
                client
                    .procedure(
                        nsid,
                        CoverageProcedureRequest("hello"),
                        CoverageProcedureRequest.serializer(),
                        CoverageProcedureResponse.serializer(),
                    ).ok,
            )
            assertEquals(
                true,
                client
                    .procedure<CoverageProcedureRequest, CoverageProcedureResponse>(
                        nsid,
                        CoverageProcedureRequest("hello"),
                    ).ok,
            )

            val builder = XrpcRequestBuilder()
            builder.queryParameter("cursor", "abc")
            builder.header("X-Test", "value")

            assertEquals(listOf("cursor" to "abc"), builder.queryParameters)
            assertEquals(listOf("X-Test" to "value"), builder.headers)
            assertIs<NoAuth>(XrpcRequestBuilder().auth)
            assertEquals("token-123", typedProcedure.auth)
            assertEquals("token-123", BearerToken("token-123").token)
        }

    @Test
    fun exceptionMessagesAreStable() {
        val transportWithoutCause = XrpcTransportException("transport only")
        val transport = XrpcTransportException("transport broke", IllegalStateException("boom"))
        val protocol = XrpcProtocolException(statusCode = 401, error = "AuthRequired", errorMessage = "token missing")
        val serializationWithoutCause = XrpcSerializationException("decode only")
        val serialization = XrpcSerializationException("decode broke", IllegalArgumentException("bad json"))

        assertEquals("transport only", transportWithoutCause.message)
        assertEquals("transport broke", transport.message)
        assertEquals("XRPC request failed with HTTP 401 [AuthRequired]: token missing", protocol.message)
        assertEquals("decode only", serializationWithoutCause.message)
        assertEquals("decode broke", serialization.message)
    }
}

private class FakeXrpcClient : XrpcClient {
    private val json: Json = Json

    override suspend fun queryRaw(
        nsid: Nsid,
        block: XrpcRequestBuilder.() -> Unit,
    ): String {
        XrpcRequestBuilder().apply(block)
        return """{"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}"""
    }

    override suspend fun <Response> query(
        nsid: Nsid,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit,
    ): Response {
        XrpcRequestBuilder().apply(block)
        return json.decodeFromString(deserializer, """{"did":"did:plc:ewvi7nxzyoun6zhxrhs64oiz"}""")
    }

    override suspend fun procedureRaw(
        nsid: Nsid,
        body: String?,
        block: XrpcRequestBuilder.() -> Unit,
    ): String {
        XrpcRequestBuilder().apply(block)
        return """{"ok":true}"""
    }

    override suspend fun <Body, Response> procedure(
        nsid: Nsid,
        body: Body,
        serializer: SerializationStrategy<Body>,
        deserializer: DeserializationStrategy<Response>,
        block: XrpcRequestBuilder.() -> Unit,
    ): Response {
        val builder = XrpcRequestBuilder().apply(block)
        val payload = json.encodeToString(serializer, body)
        val token = (builder.auth as? BearerToken)?.token.orEmpty()
        return json.decodeFromString(
            deserializer,
            """{"ok":${payload.contains("hello")},"auth":"$token"}""",
        )
    }
}

@Serializable
private data class CoverageQueryResponse(
    val did: String,
)

@Serializable
private data class CoverageProcedureRequest(
    val title: String,
)

@Serializable
private data class CoverageProcedureResponse(
    val ok: Boolean,
    val auth: String,
)
