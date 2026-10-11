package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PhoneLinkClientFactoryTest {
    private val origin = "https://first.test"
    private val bound = OriginBoundSession(origin, UserSession("access", "refresh", "account"))

    @Test
    fun `captured mint refresh and retry never follow a server or API version change`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val paths = mutableListOf<String>()
            val credentials = mutableListOf<String?>()
            val bodies = mutableListOf<String?>()
            val http =
                HttpClient(
                    MockEngine { request ->
                        assertEquals("first.test", request.url.host)
                        paths += request.url.encodedPath
                        credentials += request.headers[HttpHeaders.Authorization]
                        bodies += (request.body as? TextContent)?.text
                        val body =
                            when (paths.size) {
                                1 -> """{"error":{"code":"TOKEN_EXPIRED"}}"""
                                2 -> """{"success":true,"data":{"accessToken":"new-access","refreshToken":"new-refresh"}}"""
                                else -> """{"code":"code","expiresAt":9000}"""
                            }
                        respond(
                            body,
                            if (paths.size == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json() } }
            try {
                val clients = assertNotNull(PhoneLinkClientFactory(config, http).capture(bound))
                config.updateBackendUrl("https://second.test")
                config.updateApiVersion("v2")

                clients.links.mintLinkCode("access")
                val refreshed = assertNotNull(clients.refresher).refreshSession("refresh").getOrThrow()
                val code = clients.links.mintLinkCode(refreshed.accessToken).getOrThrow()

                assertEquals("code", code.code)
                assertEquals(listOf("/api/v1/auth/devices/link", "/api/v1/auth/token/refresh", "/api/v1/auth/devices/link"), paths)
                assertEquals(listOf("Bearer access", null, "Bearer new-access"), credentials)
                assertEquals("""{"refreshToken":"refresh"}""", bodies[1])
            } finally {
                http.close()
            }
        }

    @Test
    fun `a selected server different from the session cannot create clients`() =
        runTest {
            val http = HttpClient(MockEngine { error("No request expected") })
            try {
                val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://second.test")
                assertNull(PhoneLinkClientFactory(config, http).capture(bound))
            } finally {
                http.close()
            }
        }
}
