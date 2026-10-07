package app.logdate.client.device.identity.data

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.shared.model.RegisterDeviceRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AccountDeviceApiTest {
    @Test
    fun `registration uses stable device ID and the captured session origin`() =
        runTest {
            val id = Uuid.random()
            val client =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("https://journal.example.com/api/v1/devices/$id", request.url.toString())
                        assertEquals("Bearer test-access", request.headers[HttpHeaders.Authorization])
                        assertEquals(HttpMethod.Put, request.method)
                        respond("{}", HttpStatusCode.OK)
                    },
                )
            client.use {
                val session = OriginBoundSession("https://journal.example.com", UserSession("test-access", "test-refresh", "owner"))
                assertTrue(AccountDeviceApi(it).register(session, id, RegisterDeviceRequest("Test device", "ANDROID", "0.1.0")))
            }
        }

    @Test
    fun `registered device platform is read from metadata rather than its name`() =
        runTest {
            val id = Uuid.random()
            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            """[{"id":"$id","name":"Test device","platform":"MACOS","appVersion":"0.1.0","createdAt":1000,"lastActive":2000}]""",
                        )
                    },
                )
            client.use {
                val session = OriginBoundSession("https://journal.example.com", UserSession("test-access", "test-refresh", "owner"))
                val device = AccountDeviceApi(it).list(session).single()
                assertEquals("Test device", device.name)
                assertEquals("MACOS", device.platform)
            }
        }
}
