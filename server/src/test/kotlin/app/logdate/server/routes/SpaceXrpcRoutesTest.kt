@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.routes

import app.logdate.server.AuthV1TestEnvironment
import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import studio.hypertext.atproto.repo.Cid
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SpaceXrpcRoutesTest {
    @Test
    fun `the owner can write, read and list their personal space`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val put =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
                }
            assertEquals(HttpStatusCode.OK, put.status)
            assertEquals(HttpStatusCode.OK, client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc") { bearer(token) }.status)
            val list = client.get("/xrpc/app.logdate.space.listRepoOps?space=$space") { bearer(token) }.bodyAsText()
            assertTrue(""""rkey":"abc"""" in list.replace(" ", ""))
        }

    @Test
    fun `another account can't read or write it, and it looks missing`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val (_, other) = env.signedInAccount("bob")
            val space = "at://$did/space/app.logdate.space.personal/self"
            client.post("/xrpc/app.logdate.space.putRecord") {
                bearer(owner)
                json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
            }
            val read = client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc") { bearer(other) }
            assertEquals(HttpStatusCode.BadRequest, read.status)
            assertTrue("RecordNotFound" in read.bodyAsText())
            val write =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(other)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
                }
            assertEquals(HttpStatusCode.BadRequest, write.status)
            assertTrue("RecordNotFound" in write.bodyAsText())
            assertEquals(HttpStatusCode.Unauthorized, client.get("/xrpc/app.logdate.space.getRecord?space=$space&rkey=abc").status)
        }

    @Test
    fun `records that aren't padded sealed envelopes are refused`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val response =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":{"${'$'}type":"app.logdate.journal.entry","text":"plain"}}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue("InvalidRecord" in response.bodyAsText())
        }

    @Test
    fun `a changed record is a swap conflict`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val first =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
                }
            assertEquals(HttpStatusCode.OK, first.status)
            val stale =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson(fill = 2)},"swapCid":"bafybogus"}""")
                }
            assertEquals(HttpStatusCode.Conflict, stale.status)
            assertEquals("InvalidSwap", stale.errorCode())
            val existing =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson(fill = 2)},"createOnly":true}""")
                }
            assertEquals(HttpStatusCode.Conflict, existing.status)
            assertEquals("InvalidSwap", existing.errorCode())
            val delete =
                client.post("/xrpc/app.logdate.space.deleteRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","swapCid":"bafybogus"}""")
                }
            assertEquals(HttpStatusCode.Conflict, delete.status)
            assertEquals("InvalidSwap", delete.errorCode())
            assertEquals(HttpStatusCode.OK, getRecord(token, space, "abc").status)
        }

    @Test
    fun `blobs upload and read back only for the owner`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val (_, other) = env.signedInAccount("bob")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val bytes = ByteArray(600) { it.toByte() }
            val upload = uploadBlob(owner, space, bytes)
            assertEquals(HttpStatusCode.OK, upload.status)
            val blob =
                Json
                    .parseToJsonElement(upload.bodyAsText())
                    .jsonObject
                    .getValue("blob")
                    .jsonObject
            val cid =
                blob
                    .getValue("ref")
                    .jsonObject
                    .getValue("\$link")
                    .jsonPrimitive.content
            assertEquals(Cid.rawSha256(bytes).value, cid)
            assertEquals("blob", blob.getValue("\$type").jsonPrimitive.content)
            assertEquals("application/octet-stream", blob.getValue("mimeType").jsonPrimitive.content)
            assertEquals(600L, blob.getValue("size").jsonPrimitive.long)

            val read = getBlob(owner, space, cid)
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals(ContentType.Application.OctetStream, read.contentType()?.withoutParameters())
            assertContentEquals(bytes, read.bodyAsBytes())

            val stranger = getBlob(other, space, cid)
            assertEquals(HttpStatusCode.BadRequest, stranger.status)
            assertEquals("BlobNotFound", stranger.errorCode())
            val missing = getBlob(owner, space, Cid.rawSha256(byteArrayOf(9)).value)
            assertEquals(stranger.status, missing.status)
            assertContentEquals(missing.bodyAsBytes(), stranger.bodyAsBytes())
        }

    @Test
    fun `uploading the same blob twice keeps one stored copy`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val bytes = ByteArray(600) { 7 }
            assertEquals(HttpStatusCode.OK, uploadBlob(owner, space, bytes).status)
            assertEquals(HttpStatusCode.OK, uploadBlob(owner, space, bytes).status)
            val ownerId = runBlocking { requireNotNull(env.accountRepository.findByDid(did)) }.id.toString()
            val stored = env.blobStorage.listOwnerBlobPaths(java.util.UUID.fromString(ownerId))
            assertEquals(1, stored.size, "stored copies: $stored")
            assertTrue(stored.single().startsWith("users/$ownerId/spaces/"))
            assertContentEquals(bytes, getBlob(owner, space, Cid.rawSha256(bytes).value).bodyAsBytes())
        }

    @Test
    fun `blobs over 100 MiB are refused`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val response = uploadBlob(owner, space, ByteArray(100 * 1024 * 1024 + 1))
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals("BlobTooLarge", response.errorCode())
        }

    @Test
    fun `a record at the largest sealed size is accepted`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val largest = 61 * 65_536 + 16
            val put =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"big","record":${sealedJson(ciphertextBytes = largest)}}""")
                }
            assertEquals(HttpStatusCode.OK, put.status, put.bodyAsText())
            assertEquals(HttpStatusCode.OK, getRecord(token, space, "big").status)
        }

    @Test
    fun `every method looks missing to another account`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val (_, other) = env.signedInAccount("bob")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val bytes = ByteArray(600) { 3 }
            assertEquals(HttpStatusCode.OK, uploadBlob(owner, space, bytes).status)
            client.post("/xrpc/app.logdate.space.putRecord") {
                bearer(owner)
                json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
            }

            val responses =
                mapOf(
                    "putRecord" to
                        client.post("/xrpc/app.logdate.space.putRecord") {
                            bearer(other)
                            json("""{"space":"$space","rkey":"abc","record":${sealedJson(fill = 5)}}""")
                        },
                    "deleteRecord" to
                        client.post("/xrpc/app.logdate.space.deleteRecord") {
                            bearer(other)
                            json("""{"space":"$space","rkey":"abc"}""")
                        },
                    "listRepoOps" to
                        client.get("/xrpc/app.logdate.space.listRepoOps") {
                            bearer(other)
                            parameter("space", space)
                        },
                    "getRecord" to getRecord(other, space, "abc"),
                    "uploadBlob" to uploadBlob(other, space, bytes),
                    "getBlob" to getBlob(other, space, Cid.rawSha256(bytes).value),
                )
            responses.forEach { (method, response) ->
                val expected = if ("Blob" in method) "BlobNotFound" else "RecordNotFound"
                assertEquals(HttpStatusCode.BadRequest, response.status, method)
                assertEquals(expected, response.errorCode(), method)
            }

            val unchanged = getRecord(owner, space, "abc")
            assertEquals(HttpStatusCode.OK, unchanged.status)
            assertEquals(
                1L,
                Json
                    .parseToJsonElement(unchanged.bodyAsText())
                    .jsonObject
                    .getValue("seq")
                    .jsonPrimitive.long,
            )
        }

    @Test
    fun `a missing record and someone else's record answer byte for byte the same`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, owner) = env.signedInAccount("alice")
            val (otherDid, other) = env.signedInAccount("bob")
            val space = "at://$did/space/app.logdate.space.personal/self"
            client.post("/xrpc/app.logdate.space.putRecord") {
                bearer(owner)
                json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
            }
            val missing = getRecord(owner, space, "nothing-here")
            val someoneElses = getRecord(other, space, "abc")
            val unknownAuthor = getRecord(other, "at://did:plc:nobodyhere/space/app.logdate.space.personal/self", "abc")
            val otherType = getRecord(owner, "at://$did/space/app.logdate.space.group/self", "abc")
            val otherKey = getRecord(owner, "at://$did/space/app.logdate.space.personal/other", "abc")
            listOf(someoneElses, unknownAuthor, otherType, otherKey).forEach { response ->
                assertEquals(missing.status, response.status)
                assertContentEquals(missing.bodyAsBytes(), response.bodyAsBytes())
            }
            assertEquals(HttpStatusCode.BadRequest, getRecord(other, "at://$otherDid/space/app.logdate.space.personal/self", "abc").status)
        }

    @Test
    fun `deleted records are listed as tombstones and stop reading back`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            repeat(3) { index ->
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"r$index","record":${sealedJson()}}""")
                }
            }
            val delete =
                client.post("/xrpc/app.logdate.space.deleteRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"r0"}""")
                }
            assertEquals(HttpStatusCode.OK, delete.status)
            assertEquals(
                4L,
                Json
                    .parseToJsonElement(delete.bodyAsText())
                    .jsonObject
                    .getValue("seq")
                    .jsonPrimitive.long,
            )
            assertEquals("RecordNotFound", getRecord(token, space, "r0").errorCode())

            val page =
                client.get("/xrpc/app.logdate.space.listRepoOps") {
                    bearer(token)
                    parameter("space", space)
                    parameter("since", "1")
                    parameter("limit", "2")
                }
            val body = Json.parseToJsonElement(page.bodyAsText()).jsonObject
            assertEquals(3L, body.getValue("cursor").jsonPrimitive.long)
            assertEquals("true", body.getValue("hasMore").jsonPrimitive.content)
            val rest =
                client.get("/xrpc/app.logdate.space.listRepoOps") {
                    bearer(token)
                    parameter("space", space)
                    parameter("since", "3")
                    parameter("limit", "9999")
                }
            val tombstone = Json.parseToJsonElement(rest.bodyAsText()).jsonObject
            assertTrue(""""deleted":true""" in rest.bodyAsText().replace(" ", ""))
            assertEquals("false", tombstone.getValue("hasMore").jsonPrimitive.content)
            val badLimit =
                client.get("/xrpc/app.logdate.space.listRepoOps") {
                    bearer(token)
                    parameter("space", space)
                    parameter("limit", "lots")
                }
            assertEquals(HttpStatusCode.BadRequest, badLimit.status)
            assertEquals("InvalidRequest", badLimit.errorCode())
        }

    @Test
    fun `malformed requests are invalid`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = true)
            val (did, token) = env.signedInAccount("alice")
            val space = "at://$did/space/app.logdate.space.personal/self"
            val notASpace = getRecord(token, "https://example.com", "abc")
            assertEquals(HttpStatusCode.BadRequest, notASpace.status)
            assertEquals("InvalidRequest", notASpace.errorCode())
            val badKey =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"no spaces/allowed","record":${sealedJson()}}""")
                }
            assertEquals("InvalidRequest", badKey.errorCode())
            val notJson =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":""")
                }
            assertEquals(HttpStatusCode.BadRequest, notJson.status)
            assertEquals("InvalidRequest", notJson.errorCode())
            val unknownBlob =
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson()},"blobs":["bafkreinotuploaded"]}""")
                }
            assertEquals(HttpStatusCode.BadRequest, unknownBlob.status)
            assertEquals("InvalidRecord", unknownBlob.errorCode())
        }

    @Test
    fun `with the flag off every method is not implemented and the feature isn't advertised`() =
        testApplication {
            val env = configureAuthV1TestApp(personalSpaceEnabled = false)
            val (did, token) = env.signedInAccount("alice")
            val response =
                client.get("/xrpc/app.logdate.space.listRepoOps?space=at://$did/space/app.logdate.space.personal/self") { bearer(token) }
            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertTrue("MethodNotImplemented" in response.bodyAsText())
            val space = "at://$did/space/app.logdate.space.personal/self"
            listOf(
                client.post("/xrpc/app.logdate.space.putRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc","record":${sealedJson()}}""")
                },
                client.post("/xrpc/app.logdate.space.deleteRecord") {
                    bearer(token)
                    json("""{"space":"$space","rkey":"abc"}""")
                },
                getRecord(token, space, "abc"),
                uploadBlob(token, space, ByteArray(600)),
                getBlob(token, space, Cid.rawSha256(ByteArray(600)).value),
            ).forEach { assertEquals(HttpStatusCode.NotImplemented, it.status) }
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/xrpc/app.logdate.space.listRepoOps") { parameter("space", space) }.status,
            )
        }

    private suspend fun ApplicationTestBuilder.getRecord(
        token: String,
        space: String,
        rkey: String,
    ): HttpResponse =
        client.get("/xrpc/app.logdate.space.getRecord") {
            bearer(token)
            parameter("space", space)
            parameter("rkey", rkey)
        }

    private suspend fun ApplicationTestBuilder.uploadBlob(
        token: String,
        space: String,
        bytes: ByteArray,
    ): HttpResponse =
        client.post("/xrpc/app.logdate.space.uploadBlob") {
            bearer(token)
            parameter("space", space)
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            setBody(bytes)
        }

    private suspend fun ApplicationTestBuilder.getBlob(
        token: String,
        space: String,
        cid: String,
    ): HttpResponse =
        client.get("/xrpc/app.logdate.space.getBlob") {
            bearer(token)
            parameter("space", space)
            parameter("cid", cid)
        }
}

private suspend fun HttpResponse.errorCode(): String? =
    Json
        .parseToJsonElement(bodyAsText())
        .jsonObject["error"]
        ?.jsonPrimitive
        ?.content

private fun AuthV1TestEnvironment.signedInAccount(username: String): Pair<String, String> =
    runBlocking {
        val saved =
            accountRepository.save(
                Account(id = Uuid.random(), username = username, displayName = username, createdAt = Clock.System.now()),
            )
        val account = atprotoIdentityService.ensureIdentity(saved)
        requireNotNull(account.did) to tokenService.generateAccessToken(account.id.toString())
    }

private fun HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}

private fun HttpRequestBuilder.json(body: String) {
    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    setBody(body)
}

private fun sealedJson(
    fill: Int = 1,
    ciphertextBytes: Int = 528,
): String {
    val nonce = Base64.encode(ByteArray(12))
    val ciphertext = Base64.encode(ByteArray(ciphertextBytes) { fill.toByte() })
    return """{"${'$'}type":"app.logdate.crypto.sealed","version":1,"keyId":"abcdefghijklm",""" +
        """"nonce":{"${'$'}bytes":"$nonce"},"ciphertext":{"${'$'}bytes":"$ciphertext"}}"""
}
