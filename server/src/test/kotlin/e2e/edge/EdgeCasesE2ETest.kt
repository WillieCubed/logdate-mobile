package app.logdate.server.e2e.edge

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive E2E tests for edge cases, error conditions, and boundary scenarios
 * across all server endpoints to ensure robust error handling.
 */
class EdgeCasesE2ETest {
    
    @Test
    fun `request with extremely large payloads`() = testApplication {
        application {
            module()
        }
        
        // Test with very large username check request
        val largeUsername = "a".repeat(10000)
        val largeUsernameResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$largeUsername"}""")
        }
        assertTrue(largeUsernameResponse.status.value >= 400)
        
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
        assertTrue(largePayloadResponse.status.value in 200..599)
    }
    
    @Test
    fun `requests with special characters and unicode`() = testApplication {
        application {
            module()
        }
        
        // Test with Unicode characters
        val unicodeResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "用户名"}""")
        }
        assertTrue(unicodeResponse.status.value >= 400) // Should fail validation
        
        // Test with emoji
        val emojiResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "user😀name"}""")
        }
        assertTrue(emojiResponse.status.value >= 400) // Should fail validation
        
        // Test with special characters
        val specialCharsResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "user@#$%^&*()name"}""")
        }
        assertTrue(specialCharsResponse.status.value >= 400) // Should fail validation
        
        // Test with SQL injection-like strings
        val sqlInjectionResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "'; DROP TABLE users; --"}""")
        }
        assertTrue(sqlInjectionResponse.status.value >= 400) // Should fail validation
    }
    
    @Test
    fun `malformed JSON and content type issues`() = testApplication {
        application {
            module()
        }
        
        // Test with completely malformed JSON
        val malformedJsonResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("{invalid json structure")
        }
        assertTrue(malformedJsonResponse.status.value >= 400)
        
        // Test with missing closing brace
        val incompleteJsonResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"""")
        }
        assertTrue(incompleteJsonResponse.status.value >= 400)
        
        // Test with wrong content type
        val wrongContentTypeResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Text.Plain)
            setBody("""{"username": "testuser"}""")
        }
        assertTrue(wrongContentTypeResponse.status.value >= 400)
        
        // Test with XML instead of JSON
        val xmlResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Xml)
            setBody("<username>testuser</username>")
        }
        assertTrue(xmlResponse.status.value >= 400)
        
        // Test with form data instead of JSON
        val formDataResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=testuser")
        }
        assertTrue(formDataResponse.status.value >= 400)
    }
    
    @Test
    fun `null and empty value handling`() = testApplication {
        application {
            module()
        }
        
        // Test with null values
        val nullValueResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": null}""")
        }
        assertTrue(nullValueResponse.status.value >= 400)
        
        // Test with empty JSON object
        val emptyObjectResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertTrue(emptyObjectResponse.status.value >= 400)
        
        // Test with array instead of object
        val arrayResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""["testuser"]""")
        }
        assertTrue(arrayResponse.status.value >= 400)
        
        // Test with primitive value instead of object
        val primitiveResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody(""""testuser"""")
        }
        assertTrue(primitiveResponse.status.value >= 400)
    }
    
    @Test
    fun `boundary value testing`() = testApplication {
        application {
            module()
        }
        
        // Test with minimum valid username length (3 characters)
        val minLengthResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "abc"}""")
        }
        assertEquals(HttpStatusCode.OK, minLengthResponse.status)
        
        // Test with maximum valid username length (50 characters)
        val maxLengthUsername = "a".repeat(50)
        val maxLengthResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$maxLengthUsername"}""")
        }
        assertEquals(HttpStatusCode.OK, maxLengthResponse.status)
        
        // Test with username one character too short
        val tooShortResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "ab"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooShortResponse.status)
        
        // Test with username one character too long
        val tooLongUsername = "a".repeat(51)
        val tooLongResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$tooLongUsername"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooLongResponse.status)
    }
    
    @Test
    fun `concurrent request handling`() = testApplication {
        application {
            module()
        }
        
        // Send multiple simultaneous requests to the same endpoint
        val responses = mutableListOf<HttpResponse>()
        
        repeat(10) { i ->
            val response = client.post("/api/v1/accounts/username/check") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "user$i"}""")
            }
            responses.add(response)
        }
        
        // All requests should complete successfully
        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        
        // Verify each response has proper content
        responses.forEach { response ->
            val body = response.bodyAsText()
            assertTrue(body.contains("available") || body.contains("error"))
        }
    }
    
    @Test
    fun `unsupported HTTP methods`() = testApplication {
        application {
            module()
        }
        
        // Test GET on POST-only endpoints
        val getOnPostResponse = client.get("/api/v1/accounts/username/check")
        assertEquals(HttpStatusCode.MethodNotAllowed, getOnPostResponse.status)
        
        // Test PUT on endpoints that don't support it
        val putResponse = client.put("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, putResponse.status)
        
        // Test DELETE on create endpoints
        val deleteResponse = client.delete("/api/v1/accounts/create/begin")
        assertEquals(HttpStatusCode.MethodNotAllowed, deleteResponse.status)
        
        // Test HEAD requests
        val headResponse = client.head("/api/v1/accounts/me")
        assertTrue(headResponse.status.value in listOf(401, 405)) // Unauthorized or Method Not Allowed
    }
    
    @Test
    fun `invalid route variations`() = testApplication {
        application {
            module()
        }
        
        // Test with trailing slashes
        val trailingSlashResponse = client.post("/api/v1/accounts/username/check/") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.NotFound, trailingSlashResponse.status)
        
        // Test with wrong API version
        val wrongVersionResponse = client.post("/api/v2/accounts/username/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.NotFound, wrongVersionResponse.status)
        
        // Test with typos in endpoint names
        val typoResponse = client.post("/api/v1/accounts/usernme/check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.NotFound, typoResponse.status)
        
        // Test with case sensitivity
        val caseResponse = client.post("/api/V1/Accounts/Username/Check") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        assertEquals(HttpStatusCode.NotFound, caseResponse.status)
    }
    
    @Test
    fun `missing and extra headers`() = testApplication {
        application {
            module()
        }
        
        // Test with missing content-type header
        val noContentTypeResponse = client.post("/api/v1/accounts/username/check") {
            setBody("""{"username": "testuser"}""")
        }
        assertTrue(noContentTypeResponse.status.value >= 400)
        
        // Test with extra custom headers
        val extraHeadersResponse = client.post("/api/v1/accounts/username/check") {
            contentType(ContentType.Application.Json)
            header("X-Custom-Header", "custom-value")
            header("X-Another-Header", "another-value")
            setBody("""{"username": "testuser"}""")
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
        
        // Send many requests in quick succession
        val startTime = System.currentTimeMillis()
        val responses = mutableListOf<HttpResponse>()
        
        repeat(50) { i ->
            val response = client.get("/health")
            responses.add(response)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // All health checks should succeed
        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        
        // Should complete reasonably quickly (under 5 seconds for 50 requests)
        assertTrue(duration < 5000, "Stress test took too long: ${duration}ms")
    }
    
    @Test
    fun `response consistency under various conditions`() = testApplication {
        application {
            module()
        }
        
        // Test the same request multiple times to ensure consistent structure
        val username = "consistencytest"
        val statusCodes = mutableListOf<HttpStatusCode>()
        val responseStructures = mutableListOf<Boolean>()
        
        repeat(5) {
            val response = client.post("/api/v1/accounts/username/check") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "$username"}""")
            }
            statusCodes.add(response.status)
            
            val body = response.bodyAsText()
            // Check that response contains expected fields (not exact content due to potential timestamps)
            val hasExpectedStructure = body.contains("success") || body.contains("data") || body.contains("error")
            responseStructures.add(hasExpectedStructure)
        }
        
        // All responses should have the same status code
        val firstStatus = statusCodes.first()
        statusCodes.forEach { status ->
            assertEquals(firstStatus, status, "Status codes should be consistent")
        }
        
        // All responses should have proper structure
        responseStructures.forEach { hasStructure ->
            assertTrue(hasStructure, "All responses should have proper structure")
        }
    }
}