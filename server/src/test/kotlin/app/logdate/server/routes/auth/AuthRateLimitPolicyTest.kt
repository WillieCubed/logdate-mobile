package app.logdate.server.routes.auth

import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.routes.support.googleAuthBody
import app.logdate.server.routes.support.googleClaims
import app.logdate.server.routes.support.googleClaimsByToken
import app.logdate.server.routes.support.signinPasskeyCompleteBody
import app.logdate.server.routes.support.signupPasskeyCompleteBody
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRateLimitPolicyTest {
    @Test
    fun `authentication handlers enforce rate limits for repeated failed attempts`() =
        testApplication {
            configureAuthV1TestApp(
                googleClaimsByToken =
                    googleClaimsByToken(
                        "rate-token" to
                            googleClaims(
                                subject = "rate-sub",
                                email = "rate@example.com",
                                name = "Rate User",
                            ),
                    ),
            )

            repeat(5) {
                client.post("/api/v1/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(signupPasskeyCompleteBody(sessionToken = "missing", credentialId = "cred-$it"))
                }
            }
            val signupPasskeyLimited =
                client.post("/api/v1/auth/signup/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(signupPasskeyCompleteBody(sessionToken = "missing", credentialId = "cred-limit"))
                }
            assertEquals(HttpStatusCode.TooManyRequests, signupPasskeyLimited.status)

            repeat(5) {
                client.post("/api/v1/auth/signup/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("rate-token"))
                }
            }
            val signupGoogleLimited =
                client.post("/api/v1/auth/signup/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("rate-token"))
                }
            assertEquals(HttpStatusCode.TooManyRequests, signupGoogleLimited.status)

            repeat(10) {
                client.post("/api/v1/auth/signin/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(signinPasskeyCompleteBody(challenge = "challenge", credentialId = "cred-$it"))
                }
            }
            val signinPasskeyLimited =
                client.post("/api/v1/auth/signin/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(signinPasskeyCompleteBody(challenge = "challenge", credentialId = "cred-limit"))
                }
            assertEquals(HttpStatusCode.TooManyRequests, signinPasskeyLimited.status)

            repeat(10) {
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("rate-token"))
                }
            }
            val signinGoogleLimited =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("rate-token"))
                }
            assertEquals(HttpStatusCode.TooManyRequests, signinGoogleLimited.status)
        }
}
