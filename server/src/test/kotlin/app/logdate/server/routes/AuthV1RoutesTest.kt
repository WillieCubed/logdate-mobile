package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.routes.support.googleAuthBody
import app.logdate.server.routes.support.googleClaims
import app.logdate.server.routes.support.googleClaimsByToken
import app.logdate.server.routes.support.signinPasskeyBeginBody
import app.logdate.server.routes.support.signupPasskeyBeginBody
import app.logdate.server.routes.support.signupPasskeyCompleteBody
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuthV1RoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `google signup creates account and returns tokens`() =
        testApplication {
            configureAuthV1TestApp(
                googleClaimsByToken =
                    googleClaimsByToken(
                        "google-token-new" to
                            googleClaims(
                                subject = "google-sub-new",
                                email = "new.user@example.com",
                                name = "New User",
                            ),
                    ),
            )

            val response =
                client.post("/api/v1/auth/signup/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("google-token-new"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("true", payload["success"]?.jsonPrimitive?.content)
            val account = payload["data"]?.jsonObject?.get("account")?.jsonObject
            assertNotNull(account)
            assertEquals("new.user@example.com", account["email"]?.jsonPrimitive?.content)
            assertTrue(account["linkedProviders"]?.jsonArray?.any { it.jsonPrimitive.content == "google" } == true)
            val tokens = payload["data"]?.jsonObject?.get("tokens")?.jsonObject
            assertNotNull(tokens)
            assertTrue(tokens["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
            assertTrue(tokens["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        }

    @Test
    fun `google signin links existing verified-email account implicitly`() =
        testApplication {
            val env =
                configureAuthV1TestApp(
                    googleClaimsByToken =
                        googleClaimsByToken(
                            "google-token-link" to
                                googleClaims(
                                    subject = "google-sub-link",
                                    email = "linked.user@example.com",
                                    name = "Linked User",
                                ),
                        ),
                )

            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "linked_user",
                        displayName = "Linked User",
                        email = "linked.user@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                        lastSignInAt = Clock.System.now(),
                        isActive = true,
                    ),
                )
            }

            val response =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("google-token-link"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val account = payload["data"]?.jsonObject?.get("account")?.jsonObject
            assertNotNull(account)
            assertEquals("linked.user@example.com", account["email"]?.jsonPrimitive?.content)
            val providers = account["linkedProviders"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            assertTrue(providers.contains("google"))
        }

    @Test
    fun `google signin returns not found when no linked or matching account`() =
        testApplication {
            configureAuthV1TestApp(
                googleClaimsByToken =
                    googleClaimsByToken(
                        "google-token-missing" to
                            googleClaims(
                                subject = "google-sub-missing",
                                email = "missing.user@example.com",
                                name = "Missing User",
                            ),
                    ),
            )

            val response =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("google-token-missing"))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `passkey signup with google binding can sign in with google`() =
        testApplication {
            configureAuthV1TestApp(
                googleClaimsByToken =
                    googleClaimsByToken(
                        "g-bind" to
                            googleClaims(
                                subject = "google-sub-bind",
                                email = "bind.user@example.com",
                                name = "Bind User",
                            ),
                    ),
            )

            val beginResponse =
                client.post("/api/v1/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(signupPasskeyBeginBody(username = "bind_user", displayName = "Bind User"))
                }
            assertEquals(HttpStatusCode.OK, beginResponse.status)

            val beginPayload = json.parseToJsonElement(beginResponse.bodyAsText()).jsonObject
            val sessionToken =
                beginPayload["data"]
                    ?.jsonObject
                    ?.get("sessionToken")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(sessionToken)

            val completeResponse =
                client.post("/api/v1/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        signupPasskeyCompleteBody(
                            sessionToken = sessionToken,
                            credentialId = "test-credential-id-bind",
                            emailBindingToken = "g-bind",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, completeResponse.status)

            val googleSigninResponse =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("g-bind"))
                }
            assertEquals(HttpStatusCode.OK, googleSigninResponse.status)

            val signinPayload = json.parseToJsonElement(googleSigninResponse.bodyAsText()).jsonObject
            val account = signinPayload["data"]?.jsonObject?.get("account")?.jsonObject
            assertNotNull(account)
            assertEquals("bind.user@example.com", account["email"]?.jsonPrimitive?.content)
            val providers = account["linkedProviders"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            assertTrue(providers.contains("google"))
            assertTrue(providers.contains("passkey"))
        }

    @Test
    fun `signup passkey begin is rate limited after five requests per hour`() =
        testApplication {
            configureAuthV1TestApp()

            repeat(5) { index ->
                val response =
                    client.post("/api/v1/auth/signup/passkey/begin") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            signupPasskeyBeginBody(
                                username = "rate_limit_user_$index",
                                displayName = "Rate Limit User $index",
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }

            val blocked =
                client.post("/api/v1/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        signupPasskeyBeginBody(
                            username = "rate_limit_user_blocked",
                            displayName = "Rate Limit Blocked",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            val payload = json.parseToJsonElement(blocked.bodyAsText()).jsonObject
            assertEquals(
                "RATE_LIMIT_EXCEEDED",
                payload["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `signin passkey begin is rate limited after ten requests per minute`() =
        testApplication {
            configureAuthV1TestApp()

            repeat(10) {
                val response =
                    client.post("/api/v1/auth/signin/passkey/begin") {
                        contentType(ContentType.Application.Json)
                        setBody(signinPasskeyBeginBody("nobody"))
                    }
                assertEquals(HttpStatusCode.OK, response.status)
            }

            val blocked =
                client.post("/api/v1/auth/signin/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(signinPasskeyBeginBody("nobody"))
                }
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            val payload = json.parseToJsonElement(blocked.bodyAsText()).jsonObject
            assertEquals(
                "RATE_LIMIT_EXCEEDED",
                payload["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `auth metrics endpoints return snapshot and prometheus output`() =
        testApplication {
            configureAuthV1TestApp(
                googleClaimsByToken =
                    googleClaimsByToken(
                        "metrics-google-token" to
                            googleClaims(
                                subject = "google-sub-metrics",
                                email = "metrics.user@example.com",
                                name = "Metrics User",
                            ),
                    ),
            )

            val signup =
                client.post("/api/v1/auth/signup/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("metrics-google-token"))
                }
            assertEquals(HttpStatusCode.OK, signup.status)
            val signupPayload = json.parseToJsonElement(signup.bodyAsText()).jsonObject
            val accessToken =
                signupPayload["data"]
                    ?.jsonObject
                    ?.get("tokens")
                    ?.jsonObject
                    ?.get("accessToken")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(accessToken)

            repeat(11) {
                client.post("/api/v1/auth/signin/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(signinPasskeyBeginBody("nobody"))
                }
            }

            val metricsResponse =
                client.get("/api/v1/auth/metrics") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, metricsResponse.status)
            val metricsPayload = json.parseToJsonElement(metricsResponse.bodyAsText()).jsonObject
            val data =
                metricsPayload["data"]
                    ?.jsonObject
            assertNotNull(data)
            val rateLimited = data["rateLimitedByOperation"]?.jsonObject
            assertNotNull(rateLimited)
            assertEquals("1", rateLimited["signin.passkey.begin"]?.jsonPrimitive?.content)
            val errorsByCode = data["errorsByCode"]?.jsonObject
            assertNotNull(errorsByCode)
            assertEquals("1", errorsByCode["RATE_LIMIT_EXCEEDED"]?.jsonPrimitive?.content)

            val prometheusResponse =
                client.get("/api/v1/auth/metrics/prometheus") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.OK, prometheusResponse.status)
            val prometheusBody = prometheusResponse.bodyAsText()
            assertTrue(prometheusBody.contains("logdate_auth_operation_success_total"))
            assertTrue(prometheusBody.contains("logdate_auth_error_total"))
            assertTrue(prometheusBody.contains("logdate_auth_rate_limit_total"))
        }

    @Test
    fun `passkey delete is idempotent and unknown credential returns not found`() =
        testApplication {
            configureAuthV1TestApp()
            val credentialId = "test-credential-id-delete"

            val beginResponse =
                client.post("/api/v1/auth/signup/passkey/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(signupPasskeyBeginBody(username = "delete_user", displayName = "Delete User"))
                }
            assertEquals(HttpStatusCode.OK, beginResponse.status)

            val beginPayload = json.parseToJsonElement(beginResponse.bodyAsText()).jsonObject
            val sessionToken =
                beginPayload["data"]
                    ?.jsonObject
                    ?.get("sessionToken")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(sessionToken)

            val completeResponse =
                client.post("/api/v1/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        signupPasskeyCompleteBody(
                            sessionToken = sessionToken,
                            credentialId = credentialId,
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, completeResponse.status)

            val completePayload = json.parseToJsonElement(completeResponse.bodyAsText()).jsonObject
            val accessToken =
                completePayload["data"]
                    ?.jsonObject
                    ?.get("tokens")
                    ?.jsonObject
                    ?.get("accessToken")
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(accessToken)

            val firstDelete =
                client.delete("/api/v1/auth/me/passkeys/$credentialId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NoContent, firstDelete.status)

            val secondDelete =
                client.delete("/api/v1/auth/me/passkeys/$credentialId") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NoContent, secondDelete.status)

            val missingDelete =
                client.delete("/api/v1/auth/me/passkeys/does-not-exist") {
                    header("Authorization", "Bearer $accessToken")
                }
            assertEquals(HttpStatusCode.NotFound, missingDelete.status)
            val missingPayload = json.parseToJsonElement(missingDelete.bodyAsText()).jsonObject
            assertEquals(
                "PASSKEY_NOT_FOUND",
                missingPayload["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }
}
