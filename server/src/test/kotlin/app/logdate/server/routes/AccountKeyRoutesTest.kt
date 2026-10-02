package app.logdate.server.routes

import app.logdate.server.accountkeys.AccountKeyVault
import app.logdate.server.accountkeys.InMemoryAccountKeyRepository
import app.logdate.server.auth.InMemoryTokenService
import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountKeyRoutesTest {
    @Test
    fun `sign-in token can recover only its own key and creation is idempotent`() =
        testApplication {
            val tokens = InMemoryTokenService()
            val keys =
                object : EncryptionKeyring {
                    private val key = EncryptionKey("test-key", ByteArray(32) { it.toByte() })

                    override fun getActiveKey(): EncryptionKey = key

                    override fun getKey(keyId: String): EncryptionKey? = key.takeIf { it.keyId == keyId }
                }
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route("/api/v1") {
                        accountKeyRoutes(tokens, AccountKeyVault(InMemoryAccountKeyRepository(), keys))
                    }
                }
            }
            val accountToken = "Bearer ${tokens.generateAccessToken(UUID.randomUUID().toString())}"
            val otherToken = "Bearer ${tokens.generateAccessToken(UUID.randomUUID().toString())}"
            val path = "/api/v1/account/keys"
            val identity = Base64.getEncoder().encodeToString(ByteArray(32) { 0x31 })
            val media = Base64.getEncoder().encodeToString(ByteArray(32) { 0x44 })
            val body = """{"identityKey":"$identity","mediaKey":"$media"}"""

            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
            assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, accountToken) }.status)
            val created =
                client.put(path) {
                    header(HttpHeaders.Authorization, accountToken)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.Created, created.status)
            assertEquals("no-store", created.headers[HttpHeaders.CacheControl])
            val recovered = client.get(path) { header(HttpHeaders.Authorization, accountToken) }
            assertEquals(HttpStatusCode.OK, recovered.status)
            assertEquals("no-store", recovered.headers[HttpHeaders.CacheControl])
            val material = Json.parseToJsonElement(recovered.bodyAsText()).jsonObject
            assertEquals(identity, material.getValue("identityKey").jsonPrimitive.content)
            assertEquals(media, material.getValue("mediaKey").jsonPrimitive.content)
            assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, otherToken) }.status)
            val duplicate =
                client.put(path) {
                    header(HttpHeaders.Authorization, accountToken)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.OK, duplicate.status)
            val different = Base64.getEncoder().encodeToString(ByteArray(32) { 0x32 })
            val conflict =
                client.put(path) {
                    header(HttpHeaders.Authorization, accountToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"identityKey":"$different","mediaKey":"$media"}""")
                }
            assertEquals(HttpStatusCode.Conflict, conflict.status)
        }
}
