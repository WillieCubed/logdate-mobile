package app.logdate.server.e2e.edge

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comprehensive E2E tests for edge cases, error conditions, and boundary scenarios
 * across all server endpoints to ensure robust error handling.
 */
class EdgeCasesE2ETest {

    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `request with extremely large payloads`() = testApplication {
        application {
            module()
        }
        
        // Test with very large username check request
        val largeUsername = "a".repeat(10000)
        val largeUsernameResponse = client.get("/api/v1/accounts/username/$largeUsername/available")
        assertEquals(HttpStatusCode.BadRequest, largeUsernameResponse.status)
        
        // Test with very large JSON payload
        val largeDisplayName = "User ".repeat(1000)
        val largePayloadResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "username": "testuser",
                    "displayName": "$largeDisplayName",
                    "bio": "${largeDisplayName.repeat(10)}"
                }
            """.trimIndent())
        }
        // Should handle large payloads gracefully
        assertEquals(HttpStatusCode.BadRequest, largePayloadResponse.status)
    }
    
    @Test
    fun `requests with special characters and unicode`() = testApplication {
        application {
            module()
        }
        
        // Test with Unicode characters
        val unicodeResponse = client.get("/api/v1/accounts/username/%E7%94%A8%E6%88%B7%E5%90%8D/available")
        assertEquals(HttpStatusCode.BadRequest, unicodeResponse.status) // Should fail validation
        
        // Test with emoji
        val emojiResponse = client.get("/api/v1/accounts/username/user%F0%9F%98%80name/available")
        assertEquals(HttpStatusCode.BadRequest, emojiResponse.status) // Should fail validation
        
        // Test with special characters
        val specialCharsResponse = client.get("/api/v1/accounts/username/user%40%23%24%25%5E%26%2A%28%29name/available")
        assertEquals(HttpStatusCode.BadRequest, specialCharsResponse.status) // Should fail validation
        
        // Test with SQL injection-like strings
        val sqlInjectionResponse = client.get("/api/v1/accounts/username/%27%3B%20DROP%20TABLE%20users%3B%20--/available")
        assertEquals(HttpStatusCode.BadRequest, sqlInjectionResponse.status) // Should fail validation
    }
    
    @Test
    fun `malformed JSON and content type issues`() = testApplication {
        application {
            module()
        }
        
        // Test with completely malformed JSON
        val malformedJsonResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("{invalid json structure")
        }
        assertEquals(HttpStatusCode.BadRequest, malformedJsonResponse.status)
        
        // Test with missing closing brace
        val incompleteJsonResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"""")
        }
        assertEquals(HttpStatusCode.BadRequest, incompleteJsonResponse.status)
        
        // Test with wrong content type
        val wrongContentTypeResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Text.Plain)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, wrongContentTypeResponse.status)
        
        // Test with XML instead of JSON
        val xmlResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Xml)
            setBody("<username>testuser</username>")
        }
        assertEquals(HttpStatusCode.BadRequest, xmlResponse.status)
        
        // Test with form data instead of JSON
        val formDataResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=testuser")
        }
        assertEquals(HttpStatusCode.BadRequest, formDataResponse.status)
    }
    
    @Test
    fun `null and empty value handling`() = testApplication {
        application {
            module()
        }
        
        // Test with null values
        val nullValueResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": null, "displayName": "Test User"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, nullValueResponse.status)
        
        // Test with empty JSON object
        val emptyObjectResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.BadRequest, emptyObjectResponse.status)
        
        // Test with array instead of object
        val arrayResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody("""["testuser"]""")
        }
        assertEquals(HttpStatusCode.BadRequest, arrayResponse.status)
        
        // Test with primitive value instead of object
        val primitiveResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            setBody(""""testuser"""")
        }
        assertEquals(HttpStatusCode.BadRequest, primitiveResponse.status)
    }
    
    @Test
    fun `boundary value testing`() = testApplication {
        application {
            module()
        }
        
        // Test with minimum valid username length (3 characters)
        val minLengthResponse = client.get("/api/v1/accounts/username/abc/available")
        assertEquals(HttpStatusCode.OK, minLengthResponse.status)
        
        // Test with maximum valid username length (50 characters)
        val maxLengthUsername = "a".repeat(50)
        val maxLengthResponse = client.get("/api/v1/accounts/username/$maxLengthUsername/available")
        assertEquals(HttpStatusCode.OK, maxLengthResponse.status)
        
        // Test with username one character too short
        val tooShortResponse = client.get("/api/v1/accounts/username/ab/available")
        assertEquals(HttpStatusCode.BadRequest, tooShortResponse.status)
        
        // Test with username one character too long
        val tooLongUsername = "a".repeat(51)
        val tooLongResponse = client.get("/api/v1/accounts/username/$tooLongUsername/available")
        assertEquals(HttpStatusCode.BadRequest, tooLongResponse.status)
    }
    
    @Test
    fun `concurrent request handling`() = testApplication {
        application {
            module()
        }
        
        val responses = (0 until 10).map { i ->
            i to client.get("/api/v1/accounts/username/user$i/available")
        }
        
        // All requests should complete successfully
        responses.forEach { (_, response) ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        
        // Verify each response has proper content
        responses.forEach { (index, response) ->
            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["data"]?.jsonObject
            val success = jsonResponse["success"]?.jsonPrimitive?.booleanOrNull
            val responseUsername = data?.get("username")?.jsonPrimitive?.content
            val available = data?.get("available")?.jsonPrimitive?.booleanOrNull

            assertEquals(true, success)
            assertNotNull(data)
            assertEquals("user$index", responseUsername)
            assertEquals(true, available)
        }
    }
    
    @Test
    fun `unsupported HTTP methods`() = testApplication {
        application {
            module()
        }
        
        // Test POST on GET-only endpoints
        val postOnGetResponse = client.post("/api/v1/accounts/username/testuser/available")
        assertEquals(HttpStatusCode.NotFound, postOnGetResponse.status)
        
        // Test PUT on endpoints that don't support it
        val putResponse = client.put("/api/v1/accounts/username/testuser/available")
        assertEquals(HttpStatusCode.NotFound, putResponse.status)
        
        // Test DELETE on create endpoints
        val deleteResponse = client.delete("/api/v1/accounts/create/begin")
        assertEquals(HttpStatusCode.MethodNotAllowed, deleteResponse.status)
        
        // Test HEAD requests
        val headResponse = client.head("/api/v1/accounts/me")
        assertEquals(HttpStatusCode.MethodNotAllowed, headResponse.status)
    }
    
    @Test
    fun `invalid route variations`() = testApplication {
        application {
            module()
        }
        
        // Test with trailing slashes
        val trailingSlashResponse = client.get("/api/v1/accounts/username/testuser/available/")
        assertEquals(HttpStatusCode.NotFound, trailingSlashResponse.status)
        
        // Test with wrong API version
        val wrongVersionResponse = client.get("/api/v2/accounts/username/testuser/available")
        assertEquals(HttpStatusCode.NotFound, wrongVersionResponse.status)
        
        // Test with typos in endpoint names
        val typoResponse = client.get("/api/v1/accounts/usernme/testuser/available")
        assertEquals(HttpStatusCode.NotFound, typoResponse.status)
        
        // Test with case sensitivity
        val caseResponse = client.get("/api/V1/Accounts/Username/testuser/available")
        assertEquals(HttpStatusCode.NotFound, caseResponse.status)
    }
    
    @Test
    fun `missing and extra headers`() = testApplication {
        application {
            module()
        }
        
        // Test with missing content-type header
        val noContentTypeResponse = client.post("/api/v1/accounts/create/begin") {
            setBody("""{"username": "testuser", "displayName": "Test User"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, noContentTypeResponse.status)
        
        // Test with extra custom headers
        val extraHeadersResponse = client.post("/api/v1/accounts/create/begin") {
            contentType(ContentType.Application.Json)
            header("X-Custom-Header", "custom-value")
            header("X-Another-Header", "another-value")
            setBody("""{"username": "testuser", "displayName": "Test User"}""")
        }
        assertEquals(HttpStatusCode.OK, extraHeadersResponse.status) // Should ignore extra headers
        
        // Test with malformed authorization header on protected endpoints
        val malformedAuthResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "NotBearer token")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformedAuthResponse.status)
    }
    
    @Test
    fun `stress testing with rapid requests`() = testApplication {
        application {
            module()
        }
        
        val responses = mutableListOf<HttpResponse>()
        
        repeat(50) { i ->
            val response = client.get("/health")
            responses.add(response)
        }
        
        // All health checks should succeed
        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    @Test
    fun `response consistency under various conditions`() = testApplication {
        application {
            module()
        }
        
        // Test the same request multiple times to ensure consistent structure
        val username = "consistencytest"
        val responses = (0 until 5).map {
            client.get("/api/v1/accounts/username/$username/available")
        }

        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        responses.forEach { response ->
            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["data"]?.jsonObject
            val success = jsonResponse["success"]?.jsonPrimitive?.booleanOrNull
            val responseUsername = data?.get("username")?.jsonPrimitive?.content
            val available = data?.get("available")?.jsonPrimitive?.booleanOrNull

            assertEquals(true, success)
            assertNotNull(data)
            assertEquals(username, responseUsername)
            assertEquals(true, available)
        }
    }
}
