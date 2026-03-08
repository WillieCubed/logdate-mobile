package app.logdate.server

import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.FakeGoogleIdTokenVerifier
import app.logdate.server.auth.GoogleIdTokenClaims
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.InMemoryAccountIdentityRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.auth.InMemorySessionManager
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.auth.SessionManager
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.routes.authV1Routes
import app.logdate.util.UuidSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AuthV1TestEnvironment(
    val tokenService: JwtTokenService,
    val accountRepository: AccountRepository,
    val identityRepository: AccountIdentityRepository,
    val metrics: AuthMetricsRegistry,
)

@OptIn(ExperimentalUuidApi::class)
fun TestApplicationBuilder.configureAuthV1TestApp(
    tokenService: JwtTokenService = JwtTokenService("auth-v1-test-secret"),
    accountRepository: AccountRepository = InMemoryAccountRepository(),
    identityRepository: AccountIdentityRepository = InMemoryAccountIdentityRepository(),
    sessionManager: SessionManager = InMemorySessionManager(),
    webAuthnPasskeyService: WebAuthnPasskeyService = WebAuthnPasskeyService(),
    metrics: AuthMetricsRegistry = AuthMetricsRegistry(),
    googleIdTokenVerifier: GoogleIdTokenVerifier? = null,
    googleClaimsByToken: Map<String, GoogleIdTokenClaims> = emptyMap(),
): AuthV1TestEnvironment {
    val verifier: GoogleIdTokenVerifier = googleIdTokenVerifier ?: FakeGoogleIdTokenVerifier(googleClaimsByToken)

    val json =
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    contextual(Uuid::class, UuidSerializer)
                }
        }

    application {
        install(ContentNegotiation) {
            json(json)
        }
        routing {
            route("/api/v1") {
                authV1Routes(
                    accountRepository = accountRepository,
                    identityRepository = identityRepository,
                    sessionManager = sessionManager,
                    webAuthnService = webAuthnPasskeyService,
                    tokenService = tokenService,
                    googleIdTokenVerifier = verifier,
                    metrics = metrics,
                )
            }
        }
    }

    return AuthV1TestEnvironment(tokenService, accountRepository, identityRepository, metrics)
}
