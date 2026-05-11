package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountIdentity
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.IdentityProvider
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.routes.support.googleAuthBody
import app.logdate.server.routes.support.googleClaims
import app.logdate.server.routes.support.googleClaimsByToken
import app.logdate.server.routes.support.signinPasskeyCompleteBody
import app.logdate.shared.model.PasskeyInfo
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AuthV1RoutesEdgeCasesTest {
    @Test
    fun `username availability validates input and handles repository failures`() =
        testApplication {
            val accountRepository = mockk<AccountRepository>(relaxed = true)
            coEvery { accountRepository.usernameExists("free_user") } returns false
            coEvery { accountRepository.usernameExists("explode_user") } throws IllegalStateException("boom")
            configureAuthV1TestApp(accountRepository = accountRepository)

            val invalid = client.get("/api/v1/auth/signup/username/ab/available")
            assertEquals(HttpStatusCode.BadRequest, invalid.status)

            val reserved = client.get("/api/v1/auth/signup/username/app/available")
            assertEquals(HttpStatusCode.BadRequest, reserved.status)

            val available = client.get("/api/v1/auth/signup/username/free_user/available")
            assertEquals(HttpStatusCode.OK, available.status)

            val failure = client.get("/api/v1/auth/signup/username/explode_user/available")
            assertEquals(HttpStatusCode.InternalServerError, failure.status)
            assertTrue(failure.bodyAsText().contains("SERVER_ERROR"))
        }

    @Test
    fun `passkey signin complete touches existing passkey identity`() =
        testApplication {
            val accountId = Uuid.random()
            val passkeyService = mockk<WebAuthnPasskeyService>(relaxed = true)
            coEvery { passkeyService.verifyAuthentication(any(), any()) } returns
                WebAuthnPasskeyService.AuthenticationResult(
                    success = true,
                    userId = accountId,
                    credentialId = "cred-existing",
                )
            coEvery { passkeyService.getPasskeysForUser(any()) } returns
                listOf(
                    PasskeyInfo(
                        id = Uuid.random(),
                        credentialId = "cred-existing",
                        nickname = "Phone",
                        deviceType = "platform",
                        createdAt = Clock.System.now(),
                        lastUsedAt = Clock.System.now(),
                        isActive = true,
                    ),
                )

            val env = configureAuthV1TestApp(webAuthnPasskeyService = passkeyService)
            runBlocking {
                env.accountRepository.save(
                    Account(
                        id = accountId,
                        username = "passkey_user",
                        displayName = "Passkey User",
                        email = "passkey@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                    ),
                )
                env.identityRepository.save(
                    AccountIdentity(
                        id = Uuid.random(),
                        accountId = accountId,
                        provider = IdentityProvider.PASSKEY,
                        providerSubject = "cred-existing",
                        email = "passkey@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val response =
                client.post("/api/v1/auth/signin/passkey/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(signinPasskeyCompleteBody(challenge = "challenge", credentialId = "cred-existing"))
                }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `google signin and signup handle dangling linked identity`() =
        testApplication {
            val env =
                configureAuthV1TestApp(
                    googleClaimsByToken =
                        googleClaimsByToken(
                            "signin-dangling" to
                                googleClaims(
                                    subject = "google-signin-sub",
                                    email = "signin@example.com",
                                    name = "Signin User",
                                ),
                            "signup-dangling" to
                                googleClaims(
                                    subject = "google-signup-sub",
                                    email = "signup@example.com",
                                    name = "Signup User",
                                ),
                        ),
                )

            runBlocking {
                env.identityRepository.save(
                    AccountIdentity(
                        id = Uuid.random(),
                        accountId = Uuid.random(),
                        provider = IdentityProvider.GOOGLE,
                        providerSubject = "google-signin-sub",
                        email = "signin@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                    ),
                )
                env.identityRepository.save(
                    AccountIdentity(
                        id = Uuid.random(),
                        accountId = Uuid.random(),
                        provider = IdentityProvider.GOOGLE,
                        providerSubject = "google-signup-sub",
                        email = "signup@example.com",
                        emailVerified = true,
                        createdAt = Clock.System.now(),
                    ),
                )
            }

            val signin =
                client.post("/api/v1/auth/signin/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("signin-dangling"))
                }
            assertEquals(HttpStatusCode.NotFound, signin.status)

            val signup =
                client.post("/api/v1/auth/signup/google") {
                    contentType(ContentType.Application.Json)
                    setBody(googleAuthBody("signup-dangling"))
                }
            assertEquals(HttpStatusCode.Conflict, signup.status)
        }

    @Test
    fun `profile and metrics endpoints cover account-not-found and label escaping`() =
        testApplication {
            val env = configureAuthV1TestApp()
            val account =
                Account(
                    id = Uuid.random(),
                    username = "profile_edge",
                    displayName = "Profile Edge",
                    email = "profile.edge@example.com",
                    emailVerified = true,
                    createdAt = Clock.System.now(),
                )
            runBlocking { env.accountRepository.save(account) }

            env.metrics.recordOperation("""auth."op"\x""", 10, true)
            env.metrics.recordError("ERR\"\\\\")
            env.metrics.recordRateLimit("rl\"\\\\")

            val access = env.tokenService.generateAccessToken(account.id.toString())
            val update =
                client.put("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $access")
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName":"Updated Name"}""")
                }
            assertEquals(HttpStatusCode.OK, update.status)
            assertTrue(update.bodyAsText().contains("Updated Name"))

            val metricsProm =
                client.get("/api/v1/auth/metrics/prometheus") {
                    header(HttpHeaders.Authorization, "Bearer $access")
                }
            assertEquals(HttpStatusCode.OK, metricsProm.status)
            assertTrue(metricsProm.bodyAsText().contains("operation=\"auth.\\\"op\\\"\\\\x\""))

            val missingAccountToken = env.tokenService.generateAccessToken(Uuid.random().toString())
            val missingMe =
                client.get("/api/v1/auth/me") {
                    header(HttpHeaders.Authorization, "Bearer $missingAccountToken")
                }
            assertEquals(HttpStatusCode.NotFound, missingMe.status)

            val missingIdentities =
                client.get("/api/v1/auth/me/identities") {
                    header(HttpHeaders.Authorization, "Bearer $missingAccountToken")
                }
            assertEquals(HttpStatusCode.NotFound, missingIdentities.status)
        }
}
