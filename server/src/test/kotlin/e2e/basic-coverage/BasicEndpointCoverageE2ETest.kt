package app.logdate.server.e2e.basic

import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic endpoint coverage for the current public API surface.
 */
class BasicEndpointCoverageE2ETest {
    @Test
    fun `health endpoint returns OK`() =
        testApplication {
            application { module() }
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `root endpoint returns OK`() =
        testApplication {
            application { module() }
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("LogDate Server API v1.0", response.bodyAsText())
        }

    @Test
    fun `auth username availability validates request`() =
        testApplication {
            application { module() }
            val bad = client.get("/api/v1/auth/signup/username/%20/available")
            assertEquals(HttpStatusCode.BadRequest, bad.status)
        }

    @Test
    fun `auth passkey signup begin validates request`() =
        testApplication {
            application { module() }
            val response = client.post("/api/v1/auth/signup/passkey/begin")
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `auth passkey signin begin works with empty body`() =
        testApplication {
            application { module() }
            val response =
                client.post("/api/v1/auth/signin/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("challenge"))
        }

    @Test
    fun `auth me requires bearer token`() =
        testApplication {
            application { module() }
            val response = client.get("/api/v1/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `auth refresh requires refresh token`() =
        testApplication {
            application { module() }
            val response =
                client.post("/api/v1/auth/token/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken": ""}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `sync status requires authorization`() =
        testApplication {
            application { module() }
            val response = client.get("/api/v1/sync/status")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
