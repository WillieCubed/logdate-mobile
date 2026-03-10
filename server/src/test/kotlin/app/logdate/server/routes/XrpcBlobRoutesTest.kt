package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.repo.Cid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class XrpcBlobRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `blob xrpc endpoints upload and fetch blobs`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "alice",
                            displayName = "Alice",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())

            val upload =
                client.post("/xrpc/com.atproto.repo.uploadBlob") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    setBody(byteArrayOf(1, 2, 3))
                }
            assertEquals(HttpStatusCode.OK, upload.status)
            val uploadPayload = json.parseToJsonElement(upload.bodyAsText()).jsonObject
            val blob = uploadPayload.getValue("blob").jsonObject
            val cid =
                blob
                    .getValue("ref")
                    .jsonObject
                    .getValue("\$link")
                    .jsonPrimitive
                    .content
            val did = runBlocking { requireNotNull(env.atprotoIdentityService.findByHandle("alice.logdate.app")) }.did

            assertEquals("blob", blob.getValue("\$type").jsonPrimitive.content)
            assertEquals("image/jpeg", blob.getValue("mimeType").jsonPrimitive.content)
            assertEquals("3", blob.getValue("size").jsonPrimitive.content)

            val getBlob = client.get("/xrpc/com.atproto.sync.getBlob?did=$did&cid=$cid")

            assertEquals(HttpStatusCode.OK, getBlob.status)
            assertEquals(ContentType.Image.JPEG.toString(), getBlob.headers[HttpHeaders.ContentType])
            assertContentEquals(byteArrayOf(1, 2, 3), getBlob.bodyAsBytes())
        }

    @Test
    fun `blob xrpc endpoints validate auth and missing blobs`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val account =
                runBlocking {
                    env.accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "bob",
                            displayName = "Bob",
                            createdAt = Clock.System.now(),
                        ),
                    )
                }
            val did = runBlocking { requireNotNull(env.atprotoIdentityService.ensureIdentity(account).did) }
            val missingCid = Cid.rawSha256(byteArrayOf(9, 9, 9))
            val unauthenticated = client.post("/xrpc/com.atproto.repo.uploadBlob") { setBody(byteArrayOf(1)) }
            val missing = client.get("/xrpc/com.atproto.sync.getBlob?did=$did&cid=$missingCid")

            assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertTrue(missing.bodyAsText().contains("BlobNotFound"))
        }
}
