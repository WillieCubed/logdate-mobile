@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.routes

import app.logdate.server.auth.InMemoryTokenService
import app.logdate.server.devices.InMemoryAccountDeviceRepository
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AccountDeviceRoutesTest {
    @Test
    fun `device registration is idempotent and account bound`() =
        testApplication {
            val tokens = InMemoryTokenService()
            application {
                install(ContentNegotiation) { json() }
                routing { accountDeviceRoutes(tokens, InMemoryAccountDeviceRepository()) }
            }
            val owner = "Bearer ${tokens.generateAccessToken(Uuid.random().toString())}"
            val other = "Bearer ${tokens.generateAccessToken(Uuid.random().toString())}"
            val id = Uuid.random()
            repeat(2) {
                val registered =
                    client.put("/devices/$id") {
                        header(HttpHeaders.Authorization, owner)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test device","platform":"MACOS","appVersion":"0.1.0"}""")
                    }
                assertEquals(HttpStatusCode.OK, registered.status)
            }
            val listed = client.get("/devices") { header(HttpHeaders.Authorization, owner) }
            val devices = Json.parseToJsonElement(listed.bodyAsText()).jsonArray
            assertEquals(1, devices.size)
            assertEquals(
                "MACOS",
                devices
                    .single()
                    .jsonObject
                    .getValue("platform")
                    .jsonPrimitive.content,
            )
            assertEquals("[]", client.get("/devices") { header(HttpHeaders.Authorization, other) }.bodyAsText())
            assertEquals(HttpStatusCode.Unauthorized, client.get("/devices").status)
        }

    @Test
    fun `invalid device metadata is rejected instead of guessed`() =
        testApplication {
            val tokens = InMemoryTokenService()
            application {
                install(ContentNegotiation) { json() }
                routing { accountDeviceRoutes(tokens, InMemoryAccountDeviceRepository()) }
            }
            val owner = "Bearer ${tokens.generateAccessToken(Uuid.random().toString())}"
            for (platform in listOf("LAPTOP", "MACOS")) {
                val name = if (platform == "LAPTOP") "Test device" else ""
                val response =
                    client.put("/devices/${Uuid.random()}") {
                        header(HttpHeaders.Authorization, owner)
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"$name","platform":"$platform","appVersion":"0.1.0"}""")
                    }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }
}
