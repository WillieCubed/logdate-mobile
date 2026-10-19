package app.logdate.space

import app.logdate.lexicon.app.logdate.crypto.Sealed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.NoAuth
import studio.hypertext.atproto.xrpc.XrpcAuth
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import studio.hypertext.atproto.xrpc.XrpcSerializationException
import studio.hypertext.atproto.xrpc.XrpcTransportException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrpcDataHomeTest {
    private val space = SpaceRef.personal("did:plc:owner")
    private val sealed = Sealed(ciphertext = Bytes(ByteArray(528)), keyId = "abcdefghijklm", nonce = Bytes(ByteArray(12)), version = 1)
    private val requests = mutableListOf<HttpRequestData>()

    private fun home(
        status: HttpStatusCode,
        body: String,
        contentType: String = "application/json",
    ): XrpcDataHome = home(status, body.encodeToByteArray(), contentType)

    private fun home(
        status: HttpStatusCode,
        body: ByteArray,
        contentType: String = "application/json",
    ): XrpcDataHome {
        val engine =
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
            }
        val http = HttpClient(engine)
        return XrpcDataHome(KtorXrpcClient(http, "https://cloud.test"), http, "https://cloud.test") { BearerToken("token") }
    }

    private fun failingHome(
        auth: XrpcAuth = BearerToken("token"),
        respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): XrpcDataHome {
        val http =
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respond(request)
                },
            )
        return XrpcDataHome(KtorXrpcClient(http, "https://cloud.test"), http, "https://cloud.test") { auth }
    }

    private fun HttpRequestData.bodyBytes(): ByteArray = (body as OutgoingContent.ByteArrayContent).bytes()

    @Test
    fun `putRecord sends the space and key and envelope and swap signed in`() =
        runTest {
            val stored =
                home(HttpStatusCode.OK, """{"cid":"bafyrecord","seq":7}""")
                    .putRecord(space, "abc", sealed, blobs = listOf("bafyblob"), swap = RecordSwap.Absent)
            assertEquals(StoredRecord("abc", "bafyrecord", 7, sealed), stored)
            val request = requests.single()
            assertEquals("/xrpc/app.logdate.space.putRecord", request.url.encodedPath)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            val body = request.bodyBytes().decodeToString()
            assertTrue(""""space":"${space.uri}"""" in body)
            assertTrue(""""createOnly":true""" in body)
            assertTrue(""""blobs":["bafyblob"]""" in body)
        }

    @Test
    fun `putRecord with a matching swap sends the expected cid`() =
        runTest {
            home(HttpStatusCode.OK, """{"cid":"bafynew","seq":8}""")
                .putRecord(space, "abc", sealed, swap = RecordSwap.Matching("bafyold"))
            val body = requests.single().bodyBytes().decodeToString()
            assertTrue(""""swapCid":"bafyold"""" in body)
            assertTrue("createOnly" !in body)
            assertTrue("blobs" !in body)
        }

    @Test
    fun `a refused swap is a conflict`() =
        runTest {
            assertFailsWith<DataHomeConflictException> {
                home(HttpStatusCode.Conflict, """{"error":"InvalidSwap","message":"changed"}""")
                    .putRecord(space, "abc", sealed, swap = RecordSwap.Matching("bafyold"))
            }
        }

    @Test
    fun `other server errors are not swallowed`() =
        runTest {
            val failure =
                assertFailsWith<XrpcProtocolException> {
                    home(HttpStatusCode.InternalServerError, """{"error":"InternalServerError","message":"boom"}""")
                        .putRecord(space, "abc", sealed)
                }
            assertEquals(500, failure.statusCode)
        }

    @Test
    fun `deleteRecord sends the key and swap and returns the sequence number`() =
        runTest {
            val seq =
                home(HttpStatusCode.OK, """{"seq":9}""")
                    .deleteRecord(space, "abc", RecordSwap.Matching("bafyold"))
            assertEquals(9, seq)
            val request = requests.single()
            assertEquals("/xrpc/app.logdate.space.deleteRecord", request.url.encodedPath)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            val body = request.bodyBytes().decodeToString()
            assertTrue(""""rkey":"abc"""" in body)
            assertTrue(""""swapCid":"bafyold"""" in body)
        }

    @Test
    fun `deleting with a stale swap is a conflict`() =
        runTest {
            assertFailsWith<DataHomeConflictException> {
                home(HttpStatusCode.Conflict, """{"error":"InvalidSwap","message":"changed"}""")
                    .deleteRecord(space, "abc", RecordSwap.Matching("bafyold"))
            }
        }

    @Test
    fun `deleting with an absent swap is refused before anything is sent`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                home(HttpStatusCode.OK, """{"seq":9}""").deleteRecord(space, "abc", RecordSwap.Absent)
            }
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `getRecord returns the stored envelope`() =
        runTest {
            val ciphertext = "AAAA".repeat(176)
            val envelope =
                """{"ciphertext":{"${'$'}bytes":"$ciphertext"},"keyId":"abcdefghijklm",""" +
                    """"nonce":{"${'$'}bytes":"AAAAAAAAAAAAAAAA"},"version":1}"""
            val stored = home(HttpStatusCode.OK, """{"cid":"bafyrecord","seq":4,"value":$envelope}""").getRecord(space, "abc")
            assertEquals(StoredRecord("abc", "bafyrecord", 4, sealed), stored)
            val url = requests.single().url
            assertEquals(space.uri, url.parameters["space"])
            assertEquals("abc", url.parameters["rkey"])
        }

    @Test
    fun `a missing record reads as null`() =
        runTest {
            assertNull(home(HttpStatusCode.BadRequest, """{"error":"RecordNotFound","message":"none"}""").getRecord(space, "abc"))
        }

    @Test
    fun `getRecord propagates other errors`() =
        runTest {
            assertFailsWith<XrpcProtocolException> {
                home(HttpStatusCode.Unauthorized, """{"error":"AuthRequired","message":"sign in"}""").getRecord(space, "abc")
            }
        }

    @Test
    fun `listChanges pages by sequence number`() =
        runTest {
            val page =
                home(
                    HttpStatusCode.OK,
                    """{"ops":[{"rkey":"abc","seq":3,"deleted":true}],"cursor":3,"hasMore":false}""",
                ).listChanges(space, since = 2, limit = 50)
            assertEquals(ChangePage(listOf(RecordChange("abc", 3, true, null, null)), 3, false), page)
            val url = requests.single().url
            assertEquals("2", url.parameters["since"])
            assertEquals("50", url.parameters["limit"])
            assertEquals(space.uri, url.parameters["space"])
        }

    @Test
    fun `a cursor from before purged deletions is too old`() =
        runTest {
            assertFailsWith<DataHomeCursorTooOldException> {
                home(HttpStatusCode.BadRequest, """{"error":"CursorTooOld","message":"list again from 0"}""")
                    .listChanges(space, since = 2)
            }
        }

    @Test
    fun `listChanges propagates other errors`() =
        runTest {
            val failure =
                assertFailsWith<XrpcProtocolException> {
                    home(HttpStatusCode.BadRequest, """{"error":"InvalidRequest","message":"bad"}""").listChanges(space, since = 2)
                }
            assertEquals("InvalidRequest", failure.error)
        }

    @Test
    fun `uploadBlob sends the bytes signed in and returns the cid`() =
        runTest {
            val cid =
                home(
                    HttpStatusCode.OK,
                    """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafyblob"},"mimeType":"application/octet-stream","size":3}}""",
                ).uploadBlob(space, byteArrayOf(1, 2, 3))
            assertEquals("bafyblob", cid)
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/xrpc/app.logdate.space.uploadBlob", request.url.encodedPath)
            assertEquals(space.uri, request.url.parameters["space"])
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertContentEquals(byteArrayOf(1, 2, 3), request.bodyBytes())
        }

    @Test
    fun `a failed upload surfaces the server error`() =
        runTest {
            val failure =
                assertFailsWith<XrpcProtocolException> {
                    home(HttpStatusCode.BadRequest, """{"error":"BlobTooLarge","message":"too big"}""")
                        .uploadBlob(space, byteArrayOf(1))
                }
            assertEquals("BlobTooLarge", failure.error)
        }

    @Test
    fun `getBlob returns the stored bytes`() =
        runTest {
            val bytes =
                home(HttpStatusCode.OK, byteArrayOf(9, 8, 7), "application/octet-stream")
                    .getBlob(space, "bafyblob")
            assertContentEquals(byteArrayOf(9, 8, 7), bytes)
            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/xrpc/app.logdate.space.getBlob", request.url.encodedPath)
            assertEquals(space.uri, request.url.parameters["space"])
            assertEquals("bafyblob", request.url.parameters["cid"])
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `a missing blob reads as null`() =
        runTest {
            assertNull(home(HttpStatusCode.BadRequest, """{"error":"BlobNotFound","message":"none"}""").getBlob(space, "bafyblob"))
        }

    @Test
    fun `getBlob propagates other errors`() =
        runTest {
            assertFailsWith<XrpcProtocolException> {
                home(HttpStatusCode.Forbidden, """{"error":"Forbidden","message":"no"}""").getBlob(space, "bafyblob")
            }
        }

    @Test
    fun `a blob request that fails in transit is a transport error`() =
        runTest {
            val home = failingHome { throw IOException("connection reset") }
            assertFailsWith<XrpcTransportException> { home.uploadBlob(space, byteArrayOf(1)) }
            assertFailsWith<XrpcTransportException> { home.getBlob(space, "bafyblob") }
        }

    @Test
    fun `an upload response that isn't a blob reference is a serialization error`() =
        runTest {
            assertFailsWith<XrpcSerializationException> {
                home(HttpStatusCode.OK, """{"blob":"nope"}""").uploadBlob(space, byteArrayOf(1))
            }
            assertFailsWith<XrpcSerializationException> {
                home(HttpStatusCode.OK, "not json").uploadBlob(space, byteArrayOf(1))
            }
        }

    @Test
    fun `blob methods without a bearer token are refused before anything is sent`() =
        runTest {
            val home = failingHome(auth = NoAuth) { respond("", HttpStatusCode.OK) }
            assertFailsWith<IllegalStateException> { home.uploadBlob(space, byteArrayOf(1)) }
            assertFailsWith<IllegalStateException> { home.getBlob(space, "bafyblob") }
            assertFailsWith<IllegalStateException> { home.uploadBlob(space, 1) { Buffer().apply { writeByte(1) } } }
            assertFailsWith<IllegalStateException> { home.downloadBlob(space, "bafyblob", Buffer()) }
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `a streamed upload reads its source while sending and declares its length`() =
        runTest {
            var opened = 0
            val payload = ByteArray(200_000) { it.toByte() }
            val cid =
                home(
                    HttpStatusCode.OK,
                    """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafyblob"},"mimeType":"application/octet-stream","size":3}}""",
                ).uploadBlob(space, payload.size.toLong()) {
                    opened++
                    Buffer().apply { write(payload) }
                }

            assertEquals("bafyblob", cid)
            val request = requests.single()
            assertEquals("/xrpc/app.logdate.space.uploadBlob", request.url.encodedPath)
            assertEquals(space.uri, request.url.parameters["space"])
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            val body = request.body as OutgoingContent.WriteChannelContent
            assertEquals(payload.size.toLong(), body.contentLength)
            assertContentEquals(payload, body.toByteArray())
            assertTrue(opened >= 1)
        }

    @Test
    fun `a streamed download writes the blob into the sink`() =
        runTest {
            val payload = ByteArray(150_000) { (it * 7).toByte() }
            val sink = Buffer()

            val found = home(HttpStatusCode.OK, payload, "application/octet-stream").downloadBlob(space, "bafyblob", sink)

            assertTrue(found)
            assertContentEquals(payload, sink.readByteArray())
            val request = requests.single()
            assertEquals("/xrpc/app.logdate.space.getBlob", request.url.encodedPath)
            assertEquals("bafyblob", request.url.parameters["cid"])
        }

    @Test
    fun `a streamed download of a missing blob writes nothing`() =
        runTest {
            val sink = Buffer()

            val found =
                home(HttpStatusCode.BadRequest, """{"error":"BlobNotFound","message":"none"}""").downloadBlob(space, "bafyblob", sink)

            assertEquals(false, found)
            assertEquals(0L, sink.size)
        }

    @Test
    fun `every method reports a rate limit with the seconds to wait`() =
        runTest {
            val home =
                failingHome {
                    respond(
                        """{"error":"RateLimitExceeded","message":"Too many requests. Try again in 30 seconds."}""",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("30")),
                    )
                }
            val calls: List<suspend () -> Unit> =
                listOf(
                    { home.putRecord(space, "abc", sealed) },
                    { home.deleteRecord(space, "abc") },
                    { home.getRecord(space, "abc") },
                    { home.listChanges(space, since = 0) },
                    { home.uploadBlob(space, byteArrayOf(1)) },
                    { home.getBlob(space, "bafyblob") },
                )
            calls.forEach { call ->
                val limited = assertFailsWith<DataHomeRateLimitedException> { call() }
                assertEquals(30L, limited.retryAfterSeconds)
                assertEquals("Too many requests. Try again in 30 seconds.", limited.message)
                assertEquals(429, assertIs<XrpcProtocolException>(limited.cause).statusCode)
            }
        }

    @Test
    fun `a rate limit without Retry-After has no wait`() =
        runTest {
            val limited =
                assertFailsWith<DataHomeRateLimitedException> {
                    home(HttpStatusCode.TooManyRequests, """{"error":"RateLimitExceeded","message":"slow down"}""").getRecord(space, "abc")
                }
            assertNull(limited.retryAfterSeconds)
        }

    @Test
    fun `writes and uploads past the storage quota report it`() =
        runTest {
            val body = """{"error":"QuotaExceeded","message":"Storage quota exceeded: 10 of 10 bytes in use."}"""
            val record =
                assertFailsWith<DataHomeQuotaExceededException> {
                    home(
                        HttpStatusCode.PaymentRequired,
                        body,
                    ).putRecord(space, "abc", sealed)
                }
            assertFalse("10 of 10" in record.message.orEmpty(), "the server's wording never reaches the app")
            assertEquals("QuotaExceeded", assertIs<XrpcProtocolException>(record.cause).error)
            assertFailsWith<DataHomeQuotaExceededException> { home(HttpStatusCode.PaymentRequired, body).uploadBlob(space, byteArrayOf(1)) }
        }

    @Test
    fun `file transfers report a busy server with the seconds to wait`() =
        runTest {
            val home =
                failingHome {
                    respond(
                        """{"error":"ServerBusy","message":"The server is busy with other file transfers. Try again in 5 seconds."}""",
                        HttpStatusCode.ServiceUnavailable,
                        headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("5")),
                    )
                }
            listOf<suspend () -> Unit>(
                { home.uploadBlob(space, byteArrayOf(1)) },
                { home.getBlob(space, "bafyblob") },
            ).forEach { call ->
                val busy = assertFailsWith<DataHomeBusyException> { call() }
                assertEquals(5L, busy.retryAfterSeconds)
                assertEquals(503, assertIs<XrpcProtocolException>(busy.cause).statusCode)
            }
        }

    @Test
    fun `a rate-limited blob request carries the server's Retry-After`() =
        runTest {
            val home =
                failingHome {
                    respond(
                        """{"error":"RateLimitExceeded","message":"slow down"}""",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "30"),
                    )
                }

            val upload = assertFailsWith<XrpcProtocolException> { home.uploadBlob(space, 1) { Buffer().apply { writeByte(1) } } }
            val download = assertFailsWith<XrpcProtocolException> { home.downloadBlob(space, "bafyblob", Buffer()) }

            assertEquals(30L, upload.retryAfterSeconds)
            assertEquals(429, download.statusCode)
            assertEquals(30L, download.retryAfterSeconds)
        }
}
