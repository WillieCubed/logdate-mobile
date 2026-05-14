package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.DigitalCredentialVerifier
import app.logdate.server.auth.EmailVerificationService
import app.logdate.server.auth.GoogleVcJwksCache
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemoryPendingEmailVerificationRepository
import app.logdate.server.auth.VerificationException
import app.logdate.server.auth.VerifiedEmailClaims
import app.logdate.server.configureAuthV1TestApp
import app.logdate.server.entitlements.Entitlement
import app.logdate.server.entitlements.EntitlementFeature
import app.logdate.server.entitlements.EntitlementLimits
import app.logdate.server.entitlements.EntitlementService
import app.logdate.server.entitlements.EntitlementStatus
import app.logdate.server.entitlements.EntitlementTier
import app.logdate.server.entitlements.UnlimitedEntitlementService
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Wire-contract tests for `/api/v1/auth/me/email/verify/{begin,complete}`.
 *
 * Service-level orchestration is already covered by [EmailVerificationServiceTest];
 * here we pin the route shapes — auth, feature-flag gating, error mapping, and
 * conflict response.
 */
@OptIn(ExperimentalUuidApi::class)
class AuthV1EmailVerificationRoutesTest {
    private val expectedAudience = "https://logdate.app/auth/email"
    private val fixedNow: Instant = Instant.fromEpochSeconds(1_775_083_422)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `begin returns 501 when no email verification service is wired`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                )

            val response =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer ${env.tokenService.generateAccessToken(accountId.toString())}")
                }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertEquals(
                "EMAIL_VERIFICATION_UNAVAILABLE",
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `begin returns 404 when entitlement flag is off`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = featureOffEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier("u@example.com"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )

            val response =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer ${env.tokenService.generateAccessToken(accountId.toString())}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                "FEATURE_DISABLED",
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `begin returns a fresh transactionId nonce and audience`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier("u@example.com"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )

            val response =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer ${env.tokenService.generateAccessToken(accountId.toString())}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["transactionId"]?.jsonPrimitive?.content)
            assertNotNull(body["nonce"]?.jsonPrimitive?.content)
            assertEquals(expectedAudience, body["audience"]?.jsonPrimitive?.content)
        }

    @Test
    fun `complete rejects unknown transaction ids with the service's reason code`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier("u@example.com"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )

            val response =
                client.post("/api/v1/auth/me/email/verify/complete") {
                    header("Authorization", "Bearer ${env.tokenService.generateAccessToken(accountId.toString())}")
                    contentType(ContentType.Application.Json)
                    setBody(completeBody(Uuid.random().toString(), "{}"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                "challenge_missing_or_consumed",
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["reason"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `complete rejects a malformed transaction id with INVALID_TRANSACTION_ID`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier("u@example.com"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )

            val response =
                client.post("/api/v1/auth/me/email/verify/complete") {
                    header("Authorization", "Bearer ${env.tokenService.generateAccessToken(accountId.toString())}")
                    contentType(ContentType.Application.Json)
                    setBody(completeBody("not-a-uuid", "{}"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                "INVALID_TRANSACTION_ID",
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["error"]
                    ?.jsonObject
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `begin then complete attaches the verified email on the happy path`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val verifiedEmail = "happy@example.com"
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier(verifiedEmail),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )
            val token = env.tokenService.generateAccessToken(accountId.toString())

            val begin =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, begin.status)
            val txId =
                json
                    .parseToJsonElement(begin.bodyAsText())
                    .jsonObject["transactionId"]
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(txId)

            val complete =
                client.post("/api/v1/auth/me/email/verify/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(completeBody(txId, "{}"))
                }

            assertEquals(HttpStatusCode.OK, complete.status)
            val completedBody = json.parseToJsonElement(complete.bodyAsText()).jsonObject
            assertEquals(verifiedEmail, completedBody["email"]?.jsonPrimitive?.content)
            val refreshed = assertNotNull(accountRepo.findById(accountId))
            assertEquals(verifiedEmail, refreshed.email)
            assertTrue(refreshed.emailVerified)
        }

    @Test
    fun `complete returns 409 when the verified email is already attached elsewhere`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            val otherAccountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            accountRepo.save(
                seedAccount(otherAccountId).copy(
                    email = "collide@example.com",
                    emailVerified = true,
                    emailVerifiedAt = fixedNow,
                ),
            )
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = acceptingVerifier("collide@example.com"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )
            val token = env.tokenService.generateAccessToken(accountId.toString())

            val begin =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer $token")
                }
            val txId =
                assertNotNull(
                    json
                        .parseToJsonElement(begin.bodyAsText())
                        .jsonObject["transactionId"]
                        ?.jsonPrimitive
                        ?.content,
                )

            val complete =
                client.post("/api/v1/auth/me/email/verify/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(completeBody(txId, "{}"))
                }

            assertEquals(HttpStatusCode.Conflict, complete.status)
            val payload = json.parseToJsonElement(complete.bodyAsText()).jsonObject
            assertEquals("email_already_attached", payload["code"]?.jsonPrimitive?.content)
            // Targeted account untouched.
            val unchanged = assertNotNull(accountRepo.findById(accountId))
            assertEquals(null, unchanged.email)
            assertTrue(!unchanged.emailVerified)
        }

    @Test
    fun `complete surfaces the verifier's reason code on signature failure`() =
        testApplication {
            val accountRepo = InMemoryAccountRepository()
            val accountId = Uuid.random()
            accountRepo.save(seedAccount(accountId))
            val env =
                configureAuthV1TestApp(
                    accountRepository = accountRepo,
                    entitlementService = UnlimitedEntitlementService(),
                    emailVerificationService =
                        EmailVerificationService(
                            pendingRepository = InMemoryPendingEmailVerificationRepository(),
                            accountRepository = accountRepo,
                            verifier = rejectingVerifier("issuer_signature_invalid"),
                            clock = { fixedNow },
                            secureRandom = SecureRandom("seed".toByteArray()),
                        ),
                )
            val token = env.tokenService.generateAccessToken(accountId.toString())

            val begin =
                client.post("/api/v1/auth/me/email/verify/begin") {
                    header("Authorization", "Bearer $token")
                }
            val txId =
                assertNotNull(
                    json
                        .parseToJsonElement(begin.bodyAsText())
                        .jsonObject["transactionId"]
                        ?.jsonPrimitive
                        ?.content,
                )

            val complete =
                client.post("/api/v1/auth/me/email/verify/complete") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(completeBody(txId, "{}"))
                }

            assertEquals(HttpStatusCode.BadRequest, complete.status)
            assertEquals(
                "issuer_signature_invalid",
                json
                    .parseToJsonElement(complete.bodyAsText())
                    .jsonObject["reason"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    // --- helpers -----------------------------------------------------------

    private fun seedAccount(id: Uuid): Account =
        Account(
            id = id,
            username = "user-${id.toString().take(8)}",
            displayName = "User",
            createdAt = fixedNow,
        )

    /** All call sites pass `"{}"` as credentialJson — verifier stubs ignore it anyway. */
    private fun completeBody(
        transactionId: String,
        credentialJson: String,
    ): String {
        val escaped = credentialJson.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"transactionId":"$transactionId","credentialJson":"$escaped"}"""
    }

    private fun acceptingVerifier(email: String): DigitalCredentialVerifier =
        AcceptingVerifier(
            VerifiedEmailClaims(
                email = email,
                name = null,
                givenName = null,
                familyName = null,
                picture = null,
                hostedDomain = "",
                verifiedAt = fixedNow,
            ),
        )

    private fun rejectingVerifier(reason: String): DigitalCredentialVerifier = RejectingVerifier(reason)

    private inner class AcceptingVerifier(
        private val claims: VerifiedEmailClaims,
    ) : DigitalCredentialVerifier(
            jwksCache = trivialCache(),
            expectedAudience = expectedAudience,
            clock = { fixedNow },
        ) {
        override suspend fun verify(
            credentialJson: String,
            expectedNonce: String,
        ): kotlin.Result<VerifiedEmailClaims> = kotlin.Result.success(claims)
    }

    private inner class RejectingVerifier(
        private val reason: String,
    ) : DigitalCredentialVerifier(
            jwksCache = trivialCache(),
            expectedAudience = expectedAudience,
            clock = { fixedNow },
        ) {
        override suspend fun verify(
            credentialJson: String,
            expectedNonce: String,
        ): kotlin.Result<VerifiedEmailClaims> = kotlin.Result.failure(VerificationException(reason))
    }

    private fun trivialCache(): GoogleVcJwksCache =
        GoogleVcJwksCache(
            jwksUrl = URI.create("https://example.invalid/jwks").toURL(),
            ttl = 1.hours,
            clock = { fixedNow },
            loader = { JWKSet(ECKeyGenerator(Curve.P_256).keyID("k").generate().toPublicJWK()) },
        )

    private fun featureOffEntitlementService(): EntitlementService =
        object : EntitlementService {
            override suspend fun resolve(accountId: UUID): Entitlement =
                Entitlement(
                    planId = "free",
                    tier = EntitlementTier.FREE,
                    status = EntitlementStatus.ACTIVE,
                    limits = EntitlementLimits(storageBytes = 0L, backupCount = 0),
                    features = mapOf(EntitlementFeature.EMAIL_VERIFICATION.key to false),
                )
        }
}
