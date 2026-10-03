@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.routes

import app.logdate.server.accountkeys.InMemoryAccountKeyEnvelopeRepository
import app.logdate.server.auth.InMemoryTokenService
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.shared.model.PasskeyInfo
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AccountKeyEnvelopeRoutesTest {
    @Test
    fun `opaque envelopes are account bound immutable and retryable`() =
        testApplication {
            val owner = Uuid.random()
            val other = Uuid.random()
            val tokens = InMemoryTokenService()
            val passkeys = InMemoryPasskeyRepository()
            passkeys.storePasskey(
                owner,
                "AQ",
                byteArrayOf(1),
                0,
                PasskeyInfo(Uuid.random(), "AQ", null, "platform", Clock.System.now(), null),
            )
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { accountKeyEnvelopeRoutes(tokens, passkeys, InMemoryAccountKeyEnvelopeRepository(), true) } }
            }
            val path = "/api/v1/account/key-envelopes/AQ"
            val ownerToken = "Bearer ${tokens.generateAccessToken(owner.toString())}"
            val otherToken = "Bearer ${tokens.generateAccessToken(other.toString())}"
            val ciphertext = Base64.getEncoder().encodeToString("LDKE1".toByteArray() + ByteArray(92) { 7 })

            suspend fun save(
                token: String,
                value: String = ciphertext,
            ) = client.put(path) {
                header(HttpHeaders.Authorization, token)
                contentType(ContentType.Application.Json)
                setBody("""{"ciphertext":"$value"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
            assertEquals(HttpStatusCode.Unauthorized, client.put(path).status)
            assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, ownerToken) }.status)
            assertEquals(HttpStatusCode.NotFound, save(otherToken).status)
            repeat(2) { assertEquals(HttpStatusCode.NoContent, save(ownerToken).status) }
            assertEquals(
                HttpStatusCode.Conflict,
                save(
                    ownerToken,
                    Base64.getEncoder().encodeToString(
                        "LDKE1".toByteArray() + ByteArray(92) { 8 },
                    ),
                ).status,
            )
            val response = client.get(path) { header(HttpHeaders.Authorization, ownerToken) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(setOf("ciphertext"), body.keys)
            assertEquals(ciphertext, body.getValue("ciphertext").jsonPrimitive.content)
            assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, otherToken) }.status)
            assertEquals(HttpStatusCode.BadRequest, save(ownerToken, Base64.getEncoder().encodeToString(ByteArray(64))).status)
            assertEquals(HttpStatusCode.BadRequest, save(ownerToken, ciphertext.dropLast(2)).status)
            assertEquals(HttpStatusCode.BadRequest, save(ownerToken, "A".repeat(4096)).status)
            passkeys.deactivatePasskey("AQ", owner)
            assertEquals(HttpStatusCode.NotFound, client.get(path) { header(HttpHeaders.Authorization, ownerToken) }.status)
            assertEquals(HttpStatusCode.NotFound, save(ownerToken).status)
        }

    @Test
    fun `unfinished envelope feature is disabled`() =
        testApplication {
            val tokens = InMemoryTokenService()
            application {
                install(ContentNegotiation) { json() }
                routing {
                    route(
                        "/api/v1",
                    ) { accountKeyEnvelopeRoutes(tokens, InMemoryPasskeyRepository(), InMemoryAccountKeyEnvelopeRepository(), false) }
                }
            }
            val token = "Bearer ${tokens.generateAccessToken(Uuid.random().toString())}"
            val response = client.get("/api/v1/account/key-envelopes/AQ") { header(HttpHeaders.Authorization, token) }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }
}
