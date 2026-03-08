package app.logdate.server.routes

import app.logdate.server.audit.AuditCategory
import app.logdate.server.audit.AuditKey
import app.logdate.server.audit.formatAuditLog
import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountIdentity
import app.logdate.server.auth.AccountIdentityRepository
import app.logdate.server.auth.AccountLinkEvent
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.AuthMetricsRegistry
import app.logdate.server.auth.GoogleIdTokenClaims
import app.logdate.server.auth.GoogleIdTokenVerifier
import app.logdate.server.auth.IdentityProvider
import app.logdate.server.auth.SessionManager
import app.logdate.server.auth.SessionType
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.shared.model.AccountInfoResponse
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.ApiError
import app.logdate.shared.model.ApiErrorResponse
import app.logdate.shared.model.AuthenticatorAssertionResponse
import app.logdate.shared.model.AuthenticatorAttestationResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.shared.model.PasskeyRegistrationResponse
import app.logdate.shared.model.UpdateAccountProfileRequest
import app.logdate.shared.model.UsernameAvailabilityData
import app.logdate.shared.model.UsernameAvailabilityResponse
import io.github.aakira.napier.Napier
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val USERNAME_MIN_LENGTH = 3
private const val USERNAME_MAX_LENGTH = 50
private const val DISPLAY_NAME_MAX_LENGTH = 100
private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]+$")
private const val EMAIL_BINDING_SOURCE_GOOGLE = "google_id_token"
private val SIGNUP_RATE_LIMIT = AuthRateLimitPolicy(maxRequests = 5, windowSeconds = 60 * 60)
private val SIGNIN_RATE_LIMIT = AuthRateLimitPolicy(maxRequests = 10, windowSeconds = 60)
private const val METRIC_AUTH_SIGNUP_USERNAME_AVAILABLE = "auth.signup.username.available"
private const val METRIC_AUTH_SIGNUP_PASSKEY_BEGIN = "auth.signup.passkey.begin"
private const val METRIC_AUTH_SIGNUP_PASSKEY_COMPLETE = "auth.signup.passkey.complete"
private const val METRIC_AUTH_SIGNUP_GOOGLE = "auth.signup.google"
private const val METRIC_AUTH_SIGNIN_PASSKEY_BEGIN = "auth.signin.passkey.begin"
private const val METRIC_AUTH_SIGNIN_PASSKEY_COMPLETE = "auth.signin.passkey.complete"
private const val METRIC_AUTH_SIGNIN_GOOGLE = "auth.signin.google"
private const val METRIC_AUTH_TOKEN_REFRESH = "auth.token.refresh"
private const val METRIC_AUTH_METRICS = "auth.metrics"
private const val METRIC_AUTH_METRICS_PROM = "auth.metrics.prometheus"

@Serializable
data class EmailBindingRequest(
    val source: String,
    val value: String,
    val nonce: String? = null,
)

@Serializable
data class SignupPasskeyBeginRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
)

@Serializable
data class SignupPasskeyBeginData(
    val sessionToken: String,
    val registrationOptions: app.logdate.shared.model.PasskeyRegistrationOptions,
)

@Serializable
data class SignupPasskeyBeginResponse(
    val success: Boolean,
    val data: SignupPasskeyBeginData,
)

@Serializable
data class SignupPasskeyCompleteRequest(
    val sessionToken: String,
    val credential: PasskeyCredentialResponse,
    val emailBinding: EmailBindingRequest? = null,
)

@Serializable
data class SigninPasskeyBeginRequest(
    val username: String? = null,
)

@Serializable
data class SigninPasskeyBeginData(
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<PasskeyAllowCredentialDto>,
    val timeout: Long,
    val userVerification: String,
)

@Serializable
data class SigninPasskeyBeginResponse(
    val success: Boolean,
    val data: SigninPasskeyBeginData,
)

@Serializable
data class PasskeyAllowCredentialDto(
    val type: String = "public-key",
    val id: String,
    val transports: List<String> = emptyList(),
)

@Serializable
data class SigninPasskeyCompleteRequest(
    val credential: PasskeyAssertionResponse,
    val challenge: String,
)

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
    val username: String? = null,
    val displayName: String? = null,
    val nonce: String? = null,
)

@Serializable
data class AuthAccountView(
    val id: String,
    val username: String,
    val displayName: String,
    val did: String? = null,
    val handle: String? = null,
    val bio: String? = null,
    val email: String? = null,
    val emailVerified: Boolean,
    val linkedProviders: List<String>,
    val passkeyCredentialIds: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AuthResponseData(
    val account: AuthAccountView,
    val tokens: AccountTokens,
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val data: AuthResponseData,
)

@Serializable
data class RefreshTokenRequestV1(
    val refreshToken: String,
)

@Serializable
data class RefreshTokenDataV1(
    val accessToken: String,
)

@Serializable
data class RefreshTokenResponseV1(
    val success: Boolean,
    val data: RefreshTokenDataV1,
)

@Serializable
data class IdentityView(
    val provider: String,
    val providerSubject: String,
    val email: String? = null,
    val emailVerified: Boolean,
    val createdAt: String,
    val lastSignInAt: String? = null,
)

@Serializable
data class IdentityListResponse(
    val success: Boolean,
    val data: List<IdentityView>,
)

@Serializable
data class AuthMetricsResponse(
    val success: Boolean,
    val data: app.logdate.server.auth.AuthMetricsSnapshot,
)

private data class GoogleResolution(
    val account: Account,
    val identity: AccountIdentity,
)

@OptIn(ExperimentalUuidApi::class)
fun Route.authV1Routes(
    accountRepository: AccountRepository,
    identityRepository: AccountIdentityRepository,
    sessionManager: SessionManager,
    webAuthnService: WebAuthnPasskeyService,
    atprotoIdentityService: AtprotoIdentityService,
    tokenService: TokenService,
    googleIdTokenVerifier: GoogleIdTokenVerifier,
    metrics: AuthMetricsRegistry,
) {
    val rateLimiter = InMemoryAuthRateLimiter()

    route("/auth") {
        route("/signup") {
            get("/username/{username}/available") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val username = call.parameters["username"]?.trim().orEmpty()
                    val validationError = validateUsername(username)
                    if (validationError != null) {
                        return@get call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", validationError, metrics)
                    }
                    val isAvailable = !accountRepository.usernameExists(username)
                    call.respond(
                        HttpStatusCode.OK,
                        UsernameAvailabilityResponse(
                            success = true,
                            data = UsernameAvailabilityData(username = username, available = isAvailable),
                        ),
                    )
                    success = true
                } catch (e: Exception) {
                    Napier.e("Failed to check username availability", e)
                    call.respondForRequestException(e, "Failed to check username availability", metrics)
                } finally {
                    metrics.recordOperation(METRIC_AUTH_SIGNUP_USERNAME_AVAILABLE, System.currentTimeMillis() - start, success)
                }
            }

            route("/passkey") {
                post("/begin") {
                    val start = System.currentTimeMillis()
                    var success = false
                    try {
                        if (!call.enforceRateLimit(rateLimiter, "signup.passkey.begin", SIGNUP_RATE_LIMIT, metrics)) {
                            return@post
                        }
                        val request = call.receive<SignupPasskeyBeginRequest>()
                        val usernameError = validateUsername(request.username)
                        if (usernameError != null) {
                            return@post call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", usernameError, metrics)
                        }
                        val displayNameError = validateDisplayName(request.displayName)
                        if (displayNameError != null) {
                            return@post call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", displayNameError, metrics)
                        }
                        if (accountRepository.usernameExists(request.username)) {
                            return@post call.respondApiError(
                                HttpStatusCode.Conflict,
                                "USERNAME_TAKEN",
                                "Username is already taken",
                                metrics,
                            )
                        }

                        val temporaryUserId = Uuid.random()
                        val registrationOptions =
                            webAuthnService.generateRegistrationOptions(
                                userId = temporaryUserId,
                                username = request.username,
                                displayName = request.displayName,
                            )

                        val session =
                            sessionManager.createAccountCreationSession(
                                temporaryUserId = temporaryUserId,
                                username = request.username,
                                displayName = request.displayName,
                                challenge = registrationOptions.challenge,
                                deviceInfo = null,
                                bio = request.bio,
                            )

                        call.respond(
                            HttpStatusCode.OK,
                            SignupPasskeyBeginResponse(
                                success = true,
                                data = SignupPasskeyBeginData(session.id, registrationOptions),
                            ),
                        )
                        success = true
                    } catch (e: Exception) {
                        Napier.e("Failed to begin passkey signup", e)
                        call.respondForRequestException(e, "Failed to begin passkey signup", metrics)
                    } finally {
                        metrics.recordOperation(METRIC_AUTH_SIGNUP_PASSKEY_BEGIN, System.currentTimeMillis() - start, success)
                    }
                }

                post("/complete") {
                    val start = System.currentTimeMillis()
                    var success = false
                    try {
                        if (!call.enforceRateLimit(rateLimiter, "signup.passkey.complete", SIGNUP_RATE_LIMIT, metrics)) {
                            return@post
                        }
                        val request = call.receive<SignupPasskeyCompleteRequest>()
                        val session = sessionManager.validateSession(request.sessionToken, SessionType.ACCOUNT_CREATION)
                        if (session == null) {
                            return@post call.respondApiError(
                                HttpStatusCode.Unauthorized,
                                "INVALID_SESSION_TOKEN",
                                "Session token is invalid or expired",
                                metrics,
                            )
                        }

                        val verificationResult =
                            webAuthnService.verifyRegistration(
                                userId = session.temporaryUserId,
                                challenge = session.challenge,
                                registrationResponse = request.credential.toPasskeyRegistrationResponse(),
                            )
                        if (!verificationResult.success) {
                            return@post call.respondApiError(
                                HttpStatusCode.BadRequest,
                                "PASSKEY_VERIFICATION_FAILED",
                                verificationResult.error ?: "Failed to verify passkey",
                                metrics,
                            )
                        }

                        val now = Clock.System.now()
                        // Keep account ID aligned with the WebAuthn user ID used during signup begin.
                        // This ensures passkey ownership resolves to the created account on signin.
                        val accountId = session.temporaryUserId
                        var account =
                            Account(
                                id = accountId,
                                username = session.username,
                                displayName = session.displayName,
                                bio = session.bio,
                                createdAt = now,
                                lastSignInAt = now,
                                isActive = true,
                            )

                        val bindingClaims = resolveEmailBinding(request.emailBinding, googleIdTokenVerifier)
                        if (request.emailBinding != null && bindingClaims == null) {
                            return@post call.respondApiError(
                                HttpStatusCode.BadRequest,
                                "EMAIL_BINDING_INVALID",
                                "Email binding could not be verified",
                                metrics,
                            )
                        }

                        if (bindingClaims != null) {
                            val existingGoogleIdentity =
                                identityRepository.findByProviderSubject(IdentityProvider.GOOGLE, bindingClaims.subject)
                            if (existingGoogleIdentity != null) {
                                return@post call.respondApiError(
                                    HttpStatusCode.Conflict,
                                    "ACCOUNT_LINK_CONFLICT",
                                    "Google identity is already linked to another account",
                                    metrics,
                                )
                            }

                            account =
                                account.copy(
                                    email = bindingClaims.email.lowercase(),
                                    emailVerified = bindingClaims.emailVerified,
                                )
                        }

                        account = accountRepository.save(account)
                        sessionManager.markSessionUsed(request.sessionToken)

                        val passkeySubject = verificationResult.credentialId ?: request.credential.id
                        identityRepository.save(
                            AccountIdentity(
                                id = Uuid.random(),
                                accountId = account.id,
                                provider = IdentityProvider.PASSKEY,
                                providerSubject = passkeySubject,
                                email = account.email,
                                emailVerified = account.emailVerified,
                                createdAt = now,
                            ),
                        )

                        if (bindingClaims != null) {
                            val googleIdentity =
                                identityRepository.save(
                                    AccountIdentity(
                                        id = Uuid.random(),
                                        accountId = account.id,
                                        provider = IdentityProvider.GOOGLE,
                                        providerSubject = bindingClaims.subject,
                                        email = bindingClaims.email.lowercase(),
                                        emailVerified = bindingClaims.emailVerified,
                                        createdAt = now,
                                        lastSignInAt = now,
                                    ),
                                )
                            identityRepository.saveLinkEvent(
                                AccountLinkEvent(
                                    id = Uuid.random(),
                                    accountId = account.id,
                                    provider = IdentityProvider.GOOGLE,
                                    providerSubject = googleIdentity.providerSubject,
                                    reason = "passkey_signup_google_binding",
                                    ipHash = call.hashRemoteIp(),
                                    userAgentHash = call.hashUserAgent(),
                                    createdAt = now,
                                ),
                            )
                        }

                        val response =
                            issueAuthResponse(
                                account = account,
                                accountRepository = accountRepository,
                                identityRepository = identityRepository,
                                webAuthnService = webAuthnService,
                                atprotoIdentityService = atprotoIdentityService,
                                tokenService = tokenService,
                            )
                        Napier.i(
                            formatAuditLog(
                                AuditCategory.AUTH_SIGNUP_PASSKEY_SUCCESS,
                                mapOf(
                                    AuditKey.ACCOUNT_ID to account.id.toString(),
                                    AuditKey.IP_HASH to call.hashRemoteIp(),
                                    AuditKey.USER_AGENT_HASH to call.hashUserAgent(),
                                ),
                            ),
                        )
                        call.respond(HttpStatusCode.Created, response)
                        success = true
                    } catch (e: Exception) {
                        Napier.e("Failed to complete passkey signup", e)
                        call.respondForRequestException(e, "Failed to complete passkey signup", metrics)
                    } finally {
                        metrics.recordOperation(METRIC_AUTH_SIGNUP_PASSKEY_COMPLETE, System.currentTimeMillis() - start, success)
                    }
                }
            }

            post("/google") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    if (!call.enforceRateLimit(rateLimiter, "signup.google", SIGNUP_RATE_LIMIT, metrics)) {
                        return@post
                    }
                    val request = call.receive<GoogleAuthRequest>()
                    val claims = googleIdTokenVerifier.verify(request.idToken, request.nonce)
                    if (claims == null) {
                        return@post call.respondApiError(
                            HttpStatusCode.Unauthorized,
                            "GOOGLE_TOKEN_INVALID",
                            "Google token is invalid",
                            metrics,
                        )
                    }
                    if (!claims.emailVerified) {
                        return@post call.respondApiError(
                            HttpStatusCode.BadRequest,
                            "GOOGLE_EMAIL_UNVERIFIED",
                            "Google account email must be verified",
                            metrics,
                        )
                    }

                    val resolution =
                        resolveGoogleAccount(
                            claims = claims,
                            accountRepository = accountRepository,
                            identityRepository = identityRepository,
                            allowCreate = true,
                            requestedUsername = request.username,
                            requestedDisplayName = request.displayName,
                            call = call,
                        )
                    if (resolution == null) {
                        return@post call.respondApiError(
                            HttpStatusCode.Conflict,
                            "ACCOUNT_LINK_CONFLICT",
                            "Google account could not be linked automatically",
                            metrics,
                        )
                    }
                    accountRepository.updateLastSignIn(resolution.account.id)

                    val response =
                        issueAuthResponse(
                            resolution.account,
                            accountRepository,
                            identityRepository,
                            webAuthnService,
                            atprotoIdentityService,
                            tokenService,
                        )
                    Napier.i(
                        formatAuditLog(
                            AuditCategory.AUTH_SIGNUP_GOOGLE_SUCCESS,
                            mapOf(
                                AuditKey.ACCOUNT_ID to resolution.account.id.toString(),
                                AuditKey.IP_HASH to call.hashRemoteIp(),
                                AuditKey.USER_AGENT_HASH to call.hashUserAgent(),
                            ),
                        ),
                    )
                    call.respond(HttpStatusCode.OK, response)
                    success = true
                } catch (e: Exception) {
                    Napier.e("Failed to complete Google signup", e)
                    call.respondForRequestException(e, "Failed to complete Google signup", metrics)
                } finally {
                    metrics.recordOperation(METRIC_AUTH_SIGNUP_GOOGLE, System.currentTimeMillis() - start, success)
                }
            }
        }

        route("/signin") {
            route("/passkey") {
                post("/begin") {
                    val start = System.currentTimeMillis()
                    var success = false
                    try {
                        if (!call.enforceRateLimit(rateLimiter, "signin.passkey.begin", SIGNIN_RATE_LIMIT, metrics)) {
                            return@post
                        }
                        val request = call.receive<SigninPasskeyBeginRequest>()
                        val account = request.username?.let { accountRepository.findByUsername(it) }
                        val allowCredentials = account?.let { webAuthnService.getUserCredentials(it.id) } ?: emptyList()
                        val authOptions =
                            webAuthnService.generateAuthenticationOptions(
                                userId = account?.id,
                                allowedCredentials = allowCredentials,
                            )

                        call.respond(
                            HttpStatusCode.OK,
                            SigninPasskeyBeginResponse(
                                success = true,
                                data =
                                    SigninPasskeyBeginData(
                                        challenge = authOptions.challenge,
                                        rpId = webAuthnService.relyingPartyId,
                                        allowCredentials = authOptions.allowCredentials.map { PasskeyAllowCredentialDto(id = it) },
                                        timeout = authOptions.timeout,
                                        userVerification = "required",
                                    ),
                            ),
                        )
                        success = true
                    } catch (e: Exception) {
                        Napier.e("Failed to begin passkey signin", e)
                        call.respondForRequestException(e, "Failed to begin passkey signin", metrics)
                    } finally {
                        metrics.recordOperation(METRIC_AUTH_SIGNIN_PASSKEY_BEGIN, System.currentTimeMillis() - start, success)
                    }
                }

                post("/complete") {
                    val start = System.currentTimeMillis()
                    var success = false
                    try {
                        if (!call.enforceRateLimit(rateLimiter, "signin.passkey.complete", SIGNIN_RATE_LIMIT, metrics)) {
                            return@post
                        }
                        val request = call.receive<SigninPasskeyCompleteRequest>()
                        val verificationResult =
                            webAuthnService.verifyAuthentication(
                                challenge = request.challenge,
                                authenticationResponse = request.credential.toPasskeyAuthenticationResponse(),
                            )
                        val accountId = verificationResult.userId
                        if (!verificationResult.success || accountId == null) {
                            return@post call.respondApiError(
                                HttpStatusCode.Unauthorized,
                                "AUTHENTICATION_FAILED",
                                verificationResult.error ?: "Failed to verify passkey",
                                metrics,
                            )
                        }

                        val account = accountRepository.findById(accountId)
                        if (account == null) {
                            return@post call.respondApiError(
                                HttpStatusCode.NotFound,
                                "ACCOUNT_NOT_FOUND",
                                "Account not found",
                                metrics,
                            )
                        }

                        accountRepository.updateLastSignIn(account.id)
                        val passkeySubject = verificationResult.credentialId ?: request.credential.id
                        val existingPasskeyIdentity =
                            identityRepository.findByProviderSubject(IdentityProvider.PASSKEY, passkeySubject)
                        if (existingPasskeyIdentity == null) {
                            identityRepository.save(
                                AccountIdentity(
                                    id = Uuid.random(),
                                    accountId = account.id,
                                    provider = IdentityProvider.PASSKEY,
                                    providerSubject = passkeySubject,
                                    email = account.email,
                                    emailVerified = account.emailVerified,
                                    createdAt = Clock.System.now(),
                                    lastSignInAt = Clock.System.now(),
                                ),
                            )
                        } else {
                            identityRepository.touchLastSignIn(existingPasskeyIdentity.id)
                        }

                        val response =
                            issueAuthResponse(
                                account = account,
                                accountRepository = accountRepository,
                                identityRepository = identityRepository,
                                webAuthnService = webAuthnService,
                                atprotoIdentityService = atprotoIdentityService,
                                tokenService = tokenService,
                            )
                        Napier.i(
                            formatAuditLog(
                                AuditCategory.AUTH_SIGNIN_PASSKEY_SUCCESS,
                                mapOf(
                                    AuditKey.ACCOUNT_ID to account.id.toString(),
                                    AuditKey.CREDENTIAL_ID_HASH to passkeySubject.sha256(),
                                    AuditKey.IP_HASH to call.hashRemoteIp(),
                                    AuditKey.USER_AGENT_HASH to call.hashUserAgent(),
                                ),
                            ),
                        )
                        call.respond(HttpStatusCode.OK, response)
                        success = true
                    } catch (e: Exception) {
                        Napier.e("Failed to complete passkey signin", e)
                        call.respondForRequestException(e, "Failed to complete passkey signin", metrics)
                    } finally {
                        metrics.recordOperation(METRIC_AUTH_SIGNIN_PASSKEY_COMPLETE, System.currentTimeMillis() - start, success)
                    }
                }
            }

            post("/google") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    if (!call.enforceRateLimit(rateLimiter, "signin.google", SIGNIN_RATE_LIMIT, metrics)) {
                        return@post
                    }
                    val request = call.receive<GoogleAuthRequest>()
                    val claims = googleIdTokenVerifier.verify(request.idToken, request.nonce)
                    if (claims == null) {
                        return@post call.respondApiError(
                            HttpStatusCode.Unauthorized,
                            "GOOGLE_TOKEN_INVALID",
                            "Google token is invalid",
                            metrics,
                        )
                    }
                    if (!claims.emailVerified) {
                        return@post call.respondApiError(
                            HttpStatusCode.BadRequest,
                            "GOOGLE_EMAIL_UNVERIFIED",
                            "Google account email must be verified",
                            metrics,
                        )
                    }

                    val resolution =
                        resolveGoogleAccount(
                            claims = claims,
                            accountRepository = accountRepository,
                            identityRepository = identityRepository,
                            allowCreate = false,
                            requestedUsername = request.username,
                            requestedDisplayName = request.displayName,
                            call = call,
                        )
                            ?: return@post call.respondApiError(
                                HttpStatusCode.NotFound,
                                "ACCOUNT_NOT_FOUND_SIGNUP_REQUIRED",
                                "No account found. Use Google signup first.",
                                metrics,
                            )
                    accountRepository.updateLastSignIn(resolution.account.id)

                    val response =
                        issueAuthResponse(
                            resolution.account,
                            accountRepository,
                            identityRepository,
                            webAuthnService,
                            atprotoIdentityService,
                            tokenService,
                        )
                    Napier.i(
                        formatAuditLog(
                            AuditCategory.AUTH_SIGNIN_GOOGLE_SUCCESS,
                            mapOf(
                                AuditKey.ACCOUNT_ID to resolution.account.id.toString(),
                                AuditKey.IP_HASH to call.hashRemoteIp(),
                                AuditKey.USER_AGENT_HASH to call.hashUserAgent(),
                            ),
                        ),
                    )
                    call.respond(HttpStatusCode.OK, response)
                    success = true
                } catch (e: Exception) {
                    Napier.e("Failed to complete Google signin", e)
                    call.respondForRequestException(e, "Failed to complete Google signin", metrics)
                } finally {
                    metrics.recordOperation(METRIC_AUTH_SIGNIN_GOOGLE, System.currentTimeMillis() - start, success)
                }
            }
        }

        route("/token") {
            post("/refresh") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val request = call.receive<RefreshTokenRequestV1>()
                    if (request.refreshToken.isBlank()) {
                        return@post call.respondApiError(
                            HttpStatusCode.Unauthorized,
                            "INVALID_REFRESH_TOKEN",
                            "Refresh token is required",
                            metrics,
                        )
                    }
                    val accountId = tokenService.validateRefreshToken(request.refreshToken)
                    if (accountId == null) {
                        return@post call.respondApiError(
                            HttpStatusCode.Unauthorized,
                            "INVALID_REFRESH_TOKEN",
                            "Invalid or expired refresh token",
                            metrics,
                        )
                    }
                    val did =
                        runCatching { Uuid.parse(accountId) }
                            .getOrNull()
                            ?.let { accountRepository.findById(it)?.did }
                    val accessToken = tokenService.generateAccessToken(accountId, did)
                    call.respond(HttpStatusCode.OK, RefreshTokenResponseV1(true, RefreshTokenDataV1(accessToken)))
                    success = true
                } catch (e: Exception) {
                    Napier.e("Failed to refresh token", e)
                    call.respondForRequestException(e, "Failed to refresh token", metrics)
                } finally {
                    metrics.recordOperation(METRIC_AUTH_TOKEN_REFRESH, System.currentTimeMillis() - start, success)
                }
            }
        }

        get("/metrics") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                if (resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics) == null) {
                    return@get
                }
                call.respond(HttpStatusCode.OK, AuthMetricsResponse(success = true, data = metrics.snapshot()))
                success = true
            } catch (e: Exception) {
                Napier.e("Failed to render auth metrics", e)
                call.respondForRequestException(e, "Failed to render auth metrics", metrics)
            } finally {
                metrics.recordOperation(METRIC_AUTH_METRICS, System.currentTimeMillis() - start, success)
            }
        }

        get("/metrics/prometheus") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                if (resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics) == null) {
                    return@get
                }
                call.respondText(metrics.snapshot().toPrometheus(), ContentType.Text.Plain)
                success = true
            } catch (e: Exception) {
                Napier.e("Failed to render auth metrics in Prometheus format", e)
                call.respondForRequestException(e, "Failed to render auth metrics", metrics)
            } finally {
                metrics.recordOperation(METRIC_AUTH_METRICS_PROM, System.currentTimeMillis() - start, success)
            }
        }

        get("/me") {
            try {
                val account =
                    resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics)
                        ?: return@get
                val response =
                    issueAuthResponse(
                        account = account,
                        accountRepository = accountRepository,
                        identityRepository = identityRepository,
                        webAuthnService = webAuthnService,
                        atprotoIdentityService = atprotoIdentityService,
                        tokenService = tokenService,
                    )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                Napier.e("Failed to get current account", e)
                call.respondForRequestException(e, "Failed to get current account", metrics)
            }
        }

        put("/me") {
            try {
                val account =
                    resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics)
                        ?: return@put
                val request = call.receive<UpdateAccountProfileRequest>()
                if (request.displayName == null && request.username == null && request.bio == null) {
                    return@put call.respondApiError(
                        HttpStatusCode.BadRequest,
                        "VALIDATION_ERROR",
                        "At least one field must be provided",
                        metrics,
                    )
                }

                request.username?.let {
                    val error = validateUsername(it)
                    if (error != null) {
                        return@put call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", error, metrics)
                    }
                    if (!it.equals(account.username, ignoreCase = false) && accountRepository.usernameExists(it)) {
                        return@put call.respondApiError(
                            HttpStatusCode.Conflict,
                            "USERNAME_TAKEN",
                            "Username is already taken",
                            metrics,
                        )
                    }
                }

                request.displayName?.let {
                    val error = validateDisplayName(it)
                    if (error != null) {
                        return@put call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", error, metrics)
                    }
                }

                val saved =
                    accountRepository.save(
                        account.copy(
                            username = request.username ?: account.username,
                            displayName = request.displayName ?: account.displayName,
                            bio = request.bio ?: account.bio,
                        ),
                    )
                val ensuredAccount = atprotoIdentityService.ensureIdentity(saved)

                call.respond(
                    HttpStatusCode.OK,
                    AccountInfoResponse(
                        success = true,
                        data =
                            app.logdate.shared.model.LogDateAccount(
                                id = ensuredAccount.id,
                                username = ensuredAccount.username,
                                displayName = ensuredAccount.displayName,
                                did = ensuredAccount.did,
                                handle = ensuredAccount.handle,
                                bio = ensuredAccount.bio,
                                passkeyCredentialIds = webAuthnService.getPasskeysForUser(ensuredAccount.id).map { it.credentialId },
                                createdAt = ensuredAccount.createdAt,
                                updatedAt = ensuredAccount.lastSignInAt ?: ensuredAccount.createdAt,
                            ),
                    ),
                )
            } catch (e: Exception) {
                Napier.e("Failed to update profile", e)
                call.respondForRequestException(e, "Failed to update profile", metrics)
            }
        }

        delete("/me/passkeys/{credentialId}") {
            try {
                val account =
                    resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics)
                        ?: return@delete
                val credentialId = call.parameters["credentialId"]?.trim().orEmpty()
                if (credentialId.isBlank()) {
                    return@delete call.respondApiError(
                        HttpStatusCode.BadRequest,
                        "VALIDATION_ERROR",
                        "Credential ID is required",
                        metrics,
                    )
                }

                if (!webAuthnService.credentialBelongsToUser(credentialId, account.id)) {
                    return@delete call.respondApiError(HttpStatusCode.NotFound, "PASSKEY_NOT_FOUND", "Passkey not found", metrics)
                }
                webAuthnService.deletePasskey(credentialId, account.id)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: Exception) {
                Napier.e("Failed to delete passkey", e)
                call.respondForRequestException(e, "Failed to delete passkey", metrics)
            }
        }

        get("/me/identities") {
            try {
                val account =
                    resolveAuthenticatedAccount(call, accountRepository, tokenService, metrics)
                        ?: return@get
                val identities = identityRepository.findByAccountId(account.id)
                val payload =
                    identities.map {
                        IdentityView(
                            provider = it.provider.name.lowercase(),
                            providerSubject = it.providerSubject,
                            email = it.email,
                            emailVerified = it.emailVerified,
                            createdAt = it.createdAt.toString(),
                            lastSignInAt = it.lastSignInAt?.toString(),
                        )
                    }
                call.respond(HttpStatusCode.OK, IdentityListResponse(true, payload))
            } catch (e: Exception) {
                Napier.e("Failed to get identities", e)
                call.respondForRequestException(e, "Failed to get identities", metrics)
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun resolveGoogleAccount(
    claims: GoogleIdTokenClaims,
    accountRepository: AccountRepository,
    identityRepository: AccountIdentityRepository,
    allowCreate: Boolean,
    requestedUsername: String?,
    requestedDisplayName: String?,
    call: ApplicationCall,
): GoogleResolution? {
    val normalizedEmail = claims.email.lowercase()
    val now = Clock.System.now()

    val googleIdentity = identityRepository.findByProviderSubject(IdentityProvider.GOOGLE, claims.subject)
    if (googleIdentity != null) {
        val account = accountRepository.findById(googleIdentity.accountId) ?: return null
        identityRepository.touchLastSignIn(googleIdentity.id)
        return GoogleResolution(account, googleIdentity)
    }

    val emailMatches = accountRepository.findByVerifiedEmail(normalizedEmail)
    if (emailMatches.size > 1) {
        Napier.w("Google linking conflict for verified email $normalizedEmail: ${emailMatches.size} accounts")
        return null
    }

    if (emailMatches.size == 1) {
        val matchedAccount = emailMatches.first()
        val identity =
            identityRepository.save(
                AccountIdentity(
                    id = Uuid.random(),
                    accountId = matchedAccount.id,
                    provider = IdentityProvider.GOOGLE,
                    providerSubject = claims.subject,
                    email = normalizedEmail,
                    emailVerified = true,
                    createdAt = now,
                    lastSignInAt = now,
                    metadataJson = "{}",
                ),
            )

        identityRepository.saveLinkEvent(
            AccountLinkEvent(
                id = Uuid.random(),
                accountId = matchedAccount.id,
                provider = IdentityProvider.GOOGLE,
                providerSubject = claims.subject,
                reason = "implicit_verified_email",
                ipHash = call.hashRemoteIp(),
                userAgentHash = call.hashUserAgent(),
                createdAt = now,
            ),
        )

        if (matchedAccount.email != normalizedEmail || !matchedAccount.emailVerified) {
            accountRepository.save(
                matchedAccount.copy(
                    email = normalizedEmail,
                    emailVerified = true,
                ),
            )
        }

        Napier.i(
            formatAuditLog(
                AuditCategory.AUTH_LINK_GOOGLE_IMPLICIT,
                mapOf(
                    AuditKey.ACCOUNT_ID to matchedAccount.id.toString(),
                    AuditKey.PROVIDER_SUBJECT_HASH to claims.subject.sha256(),
                    AuditKey.IP_HASH to call.hashRemoteIp(),
                    AuditKey.USER_AGENT_HASH to call.hashUserAgent(),
                ),
            ),
        )
        return GoogleResolution(matchedAccount.copy(email = normalizedEmail, emailVerified = true), identity)
    }

    if (!allowCreate) {
        return null
    }

    val username =
        resolveUniqueUsername(
            requestedUsername = requestedUsername,
            fallbackEmail = normalizedEmail,
            accountRepository = accountRepository,
        ) ?: return null
    val displayName = requestedDisplayName?.takeIf { it.isNotBlank() } ?: claims.name?.takeIf { it.isNotBlank() } ?: username

    val account =
        accountRepository.save(
            Account(
                id = Uuid.random(),
                username = username,
                displayName = displayName,
                email = normalizedEmail,
                emailVerified = true,
                createdAt = now,
                lastSignInAt = now,
                isActive = true,
            ),
        )

    val identity =
        identityRepository.save(
            AccountIdentity(
                id = Uuid.random(),
                accountId = account.id,
                provider = IdentityProvider.GOOGLE,
                providerSubject = claims.subject,
                email = normalizedEmail,
                emailVerified = true,
                createdAt = now,
                lastSignInAt = now,
            ),
        )

    identityRepository.saveLinkEvent(
        AccountLinkEvent(
            id = Uuid.random(),
            accountId = account.id,
            provider = IdentityProvider.GOOGLE,
            providerSubject = claims.subject,
            reason = "google_signup",
            ipHash = call.hashRemoteIp(),
            userAgentHash = call.hashUserAgent(),
            createdAt = now,
        ),
    )

    return GoogleResolution(account, identity)
}

private suspend fun resolveUniqueUsername(
    requestedUsername: String?,
    fallbackEmail: String,
    accountRepository: AccountRepository,
): String? {
    val requested = requestedUsername?.trim().orEmpty()
    val candidate = if (requested.isNotBlank()) requested else fallbackEmail.substringBefore('@').ifBlank { "user" }
    val sanitized = candidate.replace(Regex("[^a-zA-Z0-9_]"), "_").take(USERNAME_MAX_LENGTH)
    if (validateUsername(sanitized) == null && !accountRepository.usernameExists(sanitized)) {
        return sanitized
    }

    for (i in 1..1000) {
        val withSuffix = "${sanitized.take((USERNAME_MAX_LENGTH - 5).coerceAtLeast(1))}_${1000 + i}"
        if (validateUsername(withSuffix) == null && !accountRepository.usernameExists(withSuffix)) {
            return withSuffix
        }
    }

    return null
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun issueAuthResponse(
    account: Account,
    accountRepository: AccountRepository,
    identityRepository: AccountIdentityRepository,
    webAuthnService: WebAuthnPasskeyService,
    atprotoIdentityService: AtprotoIdentityService,
    tokenService: TokenService,
): AuthResponse {
    val fresh = atprotoIdentityService.ensureIdentity(accountRepository.findById(account.id) ?: account)
    val identities = identityRepository.findByAccountId(fresh.id)
    val linkedProviders = identities.map { it.provider.name.lowercase() }.distinct().sorted()
    val passkeyCredentialIds = webAuthnService.getPasskeysForUser(fresh.id).map { it.credentialId }

    val tokens =
        AccountTokens(
            accessToken = tokenService.generateAccessToken(fresh.id.toString(), fresh.did),
            refreshToken = tokenService.generateRefreshToken(fresh.id.toString(), fresh.did),
        )

    return AuthResponse(
        success = true,
        data =
            AuthResponseData(
                account =
                    AuthAccountView(
                        id = fresh.id.toString(),
                        username = fresh.username,
                        displayName = fresh.displayName,
                        did = fresh.did,
                        handle = fresh.handle,
                        bio = fresh.bio,
                        email = fresh.email,
                        emailVerified = fresh.emailVerified,
                        linkedProviders = linkedProviders,
                        passkeyCredentialIds = passkeyCredentialIds,
                        createdAt = fresh.createdAt.toString(),
                        updatedAt = (fresh.lastSignInAt ?: fresh.createdAt).toString(),
                    ),
                tokens = tokens,
            ),
    )
}

private suspend fun resolveEmailBinding(
    binding: EmailBindingRequest?,
    googleIdTokenVerifier: GoogleIdTokenVerifier,
): GoogleIdTokenClaims? {
    if (binding == null) {
        return null
    }

    if (binding.source != EMAIL_BINDING_SOURCE_GOOGLE) {
        return null
    }

    val claims = googleIdTokenVerifier.verify(binding.value, binding.nonce)
    if (claims?.emailVerified != true) {
        return null
    }

    return claims
}

private fun validateUsername(username: String): String? {
    if (username.isBlank()) {
        return "Username is required"
    }

    if (username.length < USERNAME_MIN_LENGTH || username.length > USERNAME_MAX_LENGTH) {
        return "Username must be 3-50 characters long"
    }

    if (!USERNAME_REGEX.matches(username)) {
        return "Username can only contain letters, numbers, and underscores"
    }

    return null
}

private fun validateDisplayName(displayName: String): String? {
    if (displayName.isBlank()) {
        return "Display name is required"
    }

    if (displayName.length > DISPLAY_NAME_MAX_LENGTH) {
        return "Display name must be 1-100 characters long"
    }

    return null
}

private data class AuthRateLimitPolicy(
    val maxRequests: Int,
    val windowSeconds: Int,
)

private class InMemoryAuthRateLimiter {
    private val requestsByKey = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allow(
        key: String,
        policy: AuthRateLimitPolicy,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val windowStart = nowEpochMillis - (policy.windowSeconds * 1000L)
        val bucket = requestsByKey.computeIfAbsent(key) { ArrayDeque() }

        synchronized(bucket) {
            while (bucket.isNotEmpty() && bucket.first() < windowStart) {
                bucket.removeFirst()
            }
            if (bucket.size >= policy.maxRequests) {
                return false
            }
            bucket.addLast(nowEpochMillis)
            return true
        }
    }
}

private fun PasskeyCredentialResponse.toPasskeyRegistrationResponse(): PasskeyRegistrationResponse =
    PasskeyRegistrationResponse(
        id = id,
        rawId = rawId,
        response =
            AuthenticatorAttestationResponse(
                clientDataJSON = response.clientDataJSON,
                attestationObject = response.attestationObject,
            ),
        type = type,
    )

private fun PasskeyAssertionResponse.toPasskeyAuthenticationResponse(): PasskeyAuthenticationResponse =
    PasskeyAuthenticationResponse(
        id = id,
        rawId = rawId,
        response =
            AuthenticatorAssertionResponse(
                clientDataJSON = response.clientDataJSON,
                authenticatorData = response.authenticatorData,
                signature = response.signature,
                userHandle = response.userHandle,
            ),
        type = type,
    )

@OptIn(ExperimentalUuidApi::class)
private suspend fun resolveAuthenticatedAccount(
    call: ApplicationCall,
    accountRepository: AccountRepository,
    tokenService: TokenService,
    metrics: AuthMetricsRegistry?,
): Account? {
    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required", metrics)
        return null
    }

    val token = authHeader.removePrefix("Bearer ").trim()
    if (token.isBlank()) {
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Access token is required", metrics)
        return null
    }

    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        call.respondApiError(HttpStatusCode.Unauthorized, "INVALID_TOKEN", "Invalid or expired access token", metrics)
        return null
    }

    val account = accountRepository.findById(Uuid.parse(accountId))
    if (account == null) {
        call.respondApiError(HttpStatusCode.NotFound, "ACCOUNT_NOT_FOUND", "Account not found", metrics)
        return null
    }

    return account
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    code: String,
    message: String,
    metrics: AuthMetricsRegistry?,
) {
    metrics?.recordError(code)
    respond(status, ApiErrorResponse(ApiError(code, message)))
}

private suspend fun ApplicationCall.respondForRequestException(
    error: Exception,
    fallbackMessage: String,
    metrics: AuthMetricsRegistry?,
) {
    when (error) {
        is ContentTransformationException,
        is ContentConvertException,
        is SerializationException,
        is BadRequestException,
        -> respondApiError(HttpStatusCode.BadRequest, "INVALID_REQUEST", "Request body is invalid", metrics)

        else -> respondApiError(HttpStatusCode.InternalServerError, "SERVER_ERROR", fallbackMessage, metrics)
    }
}

private suspend fun ApplicationCall.enforceRateLimit(
    rateLimiter: InMemoryAuthRateLimiter,
    operation: String,
    policy: AuthRateLimitPolicy,
    metrics: AuthMetricsRegistry?,
): Boolean {
    val ipKey = hashRemoteIp() ?: request.local.remoteHost.ifBlank { "unknown" }
    val rateLimitKey = "$operation:$ipKey"
    if (rateLimiter.allow(rateLimitKey, policy)) {
        return true
    }
    metrics?.recordRateLimit(operation)
    respondApiError(
        HttpStatusCode.TooManyRequests,
        code = "RATE_LIMIT_EXCEEDED",
        message = "Too many requests. Please retry later.",
        metrics = metrics,
    )
    return false
}

private fun ApplicationCall.hashRemoteIp(): String? {
    val value = request.local.remoteHost.ifBlank { return null }
    return value.sha256()
}

private fun ApplicationCall.hashUserAgent(): String? {
    val value = request.headers[HttpHeaders.UserAgent] ?: return null
    if (value.isBlank()) return null
    return value.sha256()
}

private fun String.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(toByteArray())
    return hash.joinToString(separator = "") { "%02x".format(it) }
}

private fun app.logdate.server.auth.AuthMetricsSnapshot.toPrometheus(): String {
    val builder = StringBuilder()
    builder.appendLine("# HELP logdate_auth_operation_success_total Successful auth operations by type.")
    builder.appendLine("# TYPE logdate_auth_operation_success_total counter")
    builder.appendLine("# HELP logdate_auth_operation_error_total Failed auth operations by type.")
    builder.appendLine("# TYPE logdate_auth_operation_error_total counter")
    builder.appendLine("# HELP logdate_auth_operation_duration_ms_total Total auth operation duration by type.")
    builder.appendLine("# TYPE logdate_auth_operation_duration_ms_total counter")
    builder.appendLine("# HELP logdate_auth_error_total Auth API errors by error code.")
    builder.appendLine("# TYPE logdate_auth_error_total counter")
    builder.appendLine("# HELP logdate_auth_rate_limit_total Auth rate-limited requests by operation.")
    builder.appendLine("# TYPE logdate_auth_rate_limit_total counter")

    operations.forEach { operation ->
        val label = escapeAuthMetricLabel(operation.name)
        builder.appendLine("logdate_auth_operation_success_total{operation=\"$label\"} ${operation.successCount}")
        builder.appendLine("logdate_auth_operation_error_total{operation=\"$label\"} ${operation.errorCount}")
        builder.appendLine("logdate_auth_operation_duration_ms_total{operation=\"$label\"} ${operation.totalDurationMs}")
    }
    errorsByCode.forEach { (code, count) ->
        val label = escapeAuthMetricLabel(code)
        builder.appendLine("logdate_auth_error_total{code=\"$label\"} $count")
    }
    rateLimitedByOperation.forEach { (operation, count) ->
        val label = escapeAuthMetricLabel(operation)
        builder.appendLine("logdate_auth_rate_limit_total{operation=\"$label\"} $count")
    }

    return builder.toString()
}

private fun escapeAuthMetricLabel(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
