package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testUsernameAvailability() = testApplication {
        application { module() }

        val response = client.get("/api/v1/accounts/username/testuser/available")
        assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("testuser", data["username"]?.jsonPrimitive?.content)
        val available = data["available"]?.jsonPrimitive?.content?.toBoolean() ?: false
        assertTrue(available)
    }

    @Test
    fun testUsernameAvailabilityValidation() = testApplication {
        application { module() }

        val response = client.get("/api/v1/accounts/username/a/available")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testRefreshWithInvalidToken() = testApplication {
        application { module() }

        val response = client.post("/api/v1/accounts/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"invalid"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testGetAccountWithoutToken() = testApplication {
        application { module() }

        val response = client.get("/api/v1/accounts/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testUpdateAccountWithoutToken() = testApplication {
        application { module() }

        val response = client.put("/api/v1/accounts/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Test"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testDeletePasskeyWithoutToken() = testApplication {
        application { module() }

        val response = client.delete("/api/v1/accounts/me/passkeys/test-credential")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
