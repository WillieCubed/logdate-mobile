package app.logdate.server.responses

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `simpleSuccess helper wraps payload and serializes`() {
        val response = simpleSuccess(data = mapOf("k" to "v"), message = "ok")

        assertEquals("ok", response.message)
        assertEquals("v", response.data["k"])
        assertTrue(response.timestamp.toString().isNotBlank())

        val serializer = SimpleSuccessResponse.serializer(MapSerializer(String.serializer(), String.serializer()))
        val encoded = json.encodeToString(serializer, response)
        assertTrue(encoded.contains("\"message\":\"ok\""))
    }

    @Test
    fun `error helper returns structured error response`() {
        val response = error(code = "BAD_REQUEST", message = "invalid", details = mapOf("field" to "name"))

        assertEquals("BAD_REQUEST", response.code)
        assertEquals("invalid", response.message)
        assertEquals("name", response.details["field"])
        assertTrue(response.timestamp.toString().isNotBlank())

        val encoded = json.encodeToString(SimpleErrorResponse.serializer(), response)
        assertTrue(encoded.contains("BAD_REQUEST"))
    }
}
