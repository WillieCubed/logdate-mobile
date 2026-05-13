package app.logdate.client.networking

import app.logdate.shared.model.CompleteEmailVerificationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Exercises the HTTP shape of [EmailVerificationApiClient] against MockEngine
 * so the begin/complete round-trip, status-code dispatch, and error mapping
 * stay locked to the wire contract.
 */
class EmailVerificationApiClientTest {
    private fun createClient(engine: MockEngine): EmailVerificationApiClient {
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = false
                        },
                    )
                }
            }
        return EmailVerificationApiClient(httpClient, TestConfigRepository())
    }

    @Test
    fun `begin posts to api v1 auth me email verify begin and parses the response`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/me/email/verify/begin", request.url.encodedPath)
                    assertEquals("Bearer access-token-xyz", request.headers[HttpHeaders.Authorization])
                    respond(
                        content =
                            """{"transactionId":"tx-1","nonce":"n0nc3","audience":"https://logdate.app/auth/email"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val result = client.begin("access-token-xyz")

            val data = assertNotNull(result.getOrNull(), "expected success: ${result.exceptionOrNull()}")
            assertEquals("tx-1", data.transactionId)
            assertEquals("n0nc3", data.nonce)
            assertEquals("https://logdate.app/auth/email", data.audience)
        }

    @Test
    fun `complete returns Success on 200`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/me/email/verify/complete", request.url.encodedPath)
                    respond(
                        content = """{"email":"u@example.com","emailVerifiedAt":"2026-05-13T00:00:00Z"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val result =
                client.complete(
                    "access-token-xyz",
                    CompleteEmailVerificationRequest(transactionId = "tx-1", credentialJson = "{}"),
                )

            val outcome = assertNotNull(result.getOrNull())
            assertTrue(outcome is EmailVerificationCompletion.Success)
            assertEquals("u@example.com", outcome.email)
            assertEquals(Instant.parse("2026-05-13T00:00:00Z"), outcome.verifiedAt)
        }

    @Test
    fun `complete returns Conflict on 409`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """{"code":"email_already_attached","message":"This email is already attached to another LogDate account."}""",
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val outcome =
                client
                    .complete(
                        "access-token-xyz",
                        CompleteEmailVerificationRequest(transactionId = "tx-1", credentialJson = "{}"),
                    ).getOrThrow()

            assertTrue(outcome is EmailVerificationCompletion.Conflict)
            assertEquals("This email is already attached to another LogDate account.", outcome.message)
        }

    @Test
    fun `complete returns Failed with reason code on 400`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"reason":"issuer_signature_invalid"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val outcome =
                client
                    .complete(
                        "access-token-xyz",
                        CompleteEmailVerificationRequest(transactionId = "tx-1", credentialJson = "{}"),
                    ).getOrThrow()

            assertTrue(outcome is EmailVerificationCompletion.Failed)
            assertEquals("issuer_signature_invalid", outcome.reason)
        }

    @Test
    fun `begin surfaces an ApiException on 500`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content =
                            """{"success":false,"error":{"code":"SERVER_EXPLODED","message":"oops"}}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val result = client.begin("access-token-xyz")

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as EmailVerificationApiException
            assertEquals("SERVER_EXPLODED", ex.code)
        }

    @Test
    fun `complete surfaces an ApiException on 5xx`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"success":false,"error":{"code":"GATEWAY_DOWN","message":"upstream gone"}}""",
                        status = HttpStatusCode.BadGateway,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createClient(engine)

            val result =
                client.complete(
                    "access-token-xyz",
                    CompleteEmailVerificationRequest(transactionId = "tx-1", credentialJson = "{}"),
                )

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as EmailVerificationApiException
            assertEquals("GATEWAY_DOWN", ex.code)
        }
}
