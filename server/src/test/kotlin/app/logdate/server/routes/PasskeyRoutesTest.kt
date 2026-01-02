package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class PasskeyRoutesTest {

    @Test
    fun `GET passkeys returns not found`() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/v1/passkeys/")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST passkeys register begin returns not found`() = testApplication {
        application {
            module()
        }

        val response = client.post("/api/v1/passkeys/register/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST passkeys register complete returns not found`() = testApplication {
        application {
            module()
        }

        val response = client.post("/api/v1/passkeys/register/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId": "test", "credential": {}}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST passkeys authenticate begin returns not found`() = testApplication {
        application {
            module()
        }

        val response = client.post("/api/v1/passkeys/authenticate/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST passkeys authenticate complete returns not found`() = testApplication {
        application {
            module()
        }

        val response = client.post("/api/v1/passkeys/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId": "test", "credential": {}}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `server health endpoint works`() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("healthy"))
    }

    @Test
    fun `server root endpoint works`() = testApplication {
        application {
            module()
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("LogDate Server API"))
    }

    @Test
    fun `invalid routes return 404`() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/v1/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
