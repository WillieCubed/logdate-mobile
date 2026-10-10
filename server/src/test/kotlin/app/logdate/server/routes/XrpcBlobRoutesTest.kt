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

/**
 * Integration tests for ATProto XRPC blob management endpoints.
 *
 * This suite verifies the implementation of `com.atproto.repo.uploadBlob` and
 * `com.atproto.sync.getBlob`. It ensures that blobs are correctly uploaded with
 * appropriate metadata (CID, mimeType, size), can be retrieved by their content
 * identifiers, and that authentication and "not found" scenarios are handled
 * according to the ATProto specification.
 */
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

            val getBlob =
                client.get("/xrpc/com.atproto.sync.getBlob?did=$did&cid=$cid") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }

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
            val accessToken = env.tokenService.generateAccessToken(account.id.toString())
            val missingCid = Cid.rawSha256(byteArrayOf(9, 9, 9))
            val unauthenticated = client.post("/xrpc/com.atproto.repo.uploadBlob") { setBody(byteArrayOf(1)) }
            val missing =
                client.get("/xrpc/com.atproto.sync.getBlob?did=$did&cid=$missingCid") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }

            assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)
            assertEquals(HttpStatusCode.NotFound, missing.status)
            assertTrue(missing.bodyAsText().contains("BlobNotFound"))
        }

    @Test
    fun `blob reads answer only the signed-in owner and never confirm another repo`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val owner =
                runBlocking {
                    env.accountRepository.save(
                        Account(id = Uuid.random(), username = "dana", displayName = "Dana", createdAt = Clock.System.now()),
                    )
                }
            val other =
                runBlocking {
                    env.accountRepository.save(
                        Account(id = Uuid.random(), username = "eli", displayName = "Eli", createdAt = Clock.System.now()),
                    )
                }
            val ownerToken = env.tokenService.generateAccessToken(owner.id.toString())
            val otherToken = env.tokenService.generateAccessToken(other.id.toString())
            val upload =
                client.post("/xrpc/com.atproto.repo.uploadBlob") {
                    header(HttpHeaders.Authorization, "Bearer $ownerToken")
                    header(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                    setBody(byteArrayOf(4, 5, 6))
                }
            assertEquals(HttpStatusCode.OK, upload.status)
            val cid =
                json
                    .parseToJsonElement(upload.bodyAsText())
                    .jsonObject
                    .getValue("blob")
                    .jsonObject
                    .getValue("ref")
                    .jsonObject
                    .getValue("\$link")
                    .jsonPrimitive
                    .content
            val ownerDid = runBlocking { requireNotNull(env.atprotoIdentityService.findByHandle("dana.logdate.app")?.did) }
            val ownerBlob = "/xrpc/com.atproto.sync.getBlob?did=$ownerDid&cid=$cid"
            val missingBlob = "/xrpc/com.atproto.sync.getBlob?did=did:plc:aaaaaaaaaaaaaaaaaaaaaaaa&cid=$cid"

            val anonymous = client.get(ownerBlob)
            val anonymousMissing = client.get(missingBlob)
            assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
            assertTrue(anonymous.bodyAsText().contains("AuthRequired"))
            assertEquals(anonymousMissing.status, anonymous.status)
            assertEquals(anonymousMissing.bodyAsText(), anonymous.bodyAsText())

            val otherReading = client.get(ownerBlob) { header(HttpHeaders.Authorization, "Bearer $otherToken") }
            val otherMissing = client.get(missingBlob) { header(HttpHeaders.Authorization, "Bearer $otherToken") }
            assertEquals(HttpStatusCode.NotFound, otherReading.status)
            assertEquals(otherMissing.status, otherReading.status)
            assertEquals(otherMissing.bodyAsText(), otherReading.bodyAsText())

            val ownerReading = client.get(ownerBlob) { header(HttpHeaders.Authorization, "Bearer $ownerToken") }
            assertEquals(HttpStatusCode.OK, ownerReading.status)
            assertContentEquals(byteArrayOf(4, 5, 6), ownerReading.bodyAsBytes())
        }
}
