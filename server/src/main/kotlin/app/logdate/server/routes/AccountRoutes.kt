package app.logdate.server.routes

import app.logdate.server.auth.*
import app.logdate.server.passkeys.*
import app.logdate.server.responses.*
import app.logdate.shared.model.*
import app.logdate.server.auth.DeviceInfo as ServerDeviceInfo
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Username availability request/response
@Serializable
data class UsernameAvailabilityRequest(
    val username: String
)

@Serializable
data class UsernameAvailabilityResponse(
    val username: String,
    val available: Boolean,
    val error: String? = null
)

// Account creation request/response models
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAccountCreationRequest(
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val deviceInfo: ServerDeviceInfo? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAccountCreationResponse(
    val sessionId: String,
    val challenge: String,
    val registrationOptions: PasskeyRegistrationOptions
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAccountCreationRequest(
    val sessionId: String,
    val credential: PasskeyRegistrationResponse,
    val accountPreferences: AccountPreferences? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAccountCreationResponse(
    val success: Boolean,
    val account: AccountInfo,
    val accessToken: String,
    val refreshToken: String,
    val passkey: PasskeyInfo
)

// Authentication request/response models
@OptIn(ExperimentalUuidApi::class)
@Serializable  
data class BeginAuthenticationRequest(
    val username: String? = null
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class BeginAuthenticationResponse(
    val sessionId: String,
    val challenge: String,
    val authenticationOptions: PasskeyAuthenticationOptions
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAuthenticationRequest(
    val sessionId: String,
    val credential: PasskeyAuthenticationResponse
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CompleteAuthenticationResponse(
    val success: Boolean,
    val account: AccountInfo,
    val accessToken: String,
    val refreshToken: String
)


@OptIn(ExperimentalUuidApi::class)
fun Route.accountRoutes() {
    val webAuthnService = app.logdate.server.database.RepositoryFactory.createWebAuthnService()
    val accountRepository: AccountRepository = app.logdate.server.database.RepositoryFactory.createAccountRepository()
    val passkeyRepository = app.logdate.server.database.RepositoryFactory.createPasskeyRepository()
    val sessionManager: SessionManager = app.logdate.server.database.RepositoryFactory.createSessionManager()
    val tokenService: TokenService = JwtTokenService()
    
    route("/accounts") {
        
        // Check username availability
        post("/username/check") {
            try {
                val request = call.receive<UsernameAvailabilityRequest>()
                
                // Validate username format
                if (request.username.isBlank() || request.username.length < 3 || request.username.length > 50) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_USERNAME", "Username must be 3-50 characters long")
                    )
                    return@post
                }
                
                if (!request.username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_USERNAME", "Username can only contain letters, numbers, and underscores")
                    )
                    return@post
                }
                
                // Check availability
                val isAvailable = !accountRepository.usernameExists(request.username)
                
                call.respond(
                    HttpStatusCode.OK,
                    success(UsernameAvailabilityResponse(
                        username = request.username,
                        available = isAvailable
                    ))
                )
            } catch (e: Exception) {
                Napier.e("Failed to check username availability", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to check username availability")
                )
            }
        }
        
        // Begin account creation with passkey
        post("/create/begin") {
            try {
                val request = call.receive<BeginAccountCreationRequest>()
                
                // Validate input
                if (request.username.isBlank() || request.displayName.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_INPUT", "Username and display name are required")
                    )
                    return@post
                }
                
                // Check username availability
                if (accountRepository.usernameExists(request.username)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        error("USERNAME_TAKEN", "Username is already taken")
                    )
                    return@post
                }
                
                // Generate temporary user ID and registration options
                val temporaryUserId = Uuid.random()
                val registrationOptions = webAuthnService.generateRegistrationOptions(
                    userId = temporaryUserId,
                    username = request.username,
                    displayName = request.displayName
                )
                
                // Create session
                val session = sessionManager.createAccountCreationSession(
                    username = request.username,
                    displayName = request.displayName,
                    challenge = registrationOptions.challenge,
                    deviceInfo = request.deviceInfo,
                    bio = request.bio
                )
                
                val response = BeginAccountCreationResponse(
                    sessionId = session.id,
                    challenge = registrationOptions.challenge,
                    registrationOptions = registrationOptions
                )
                
                call.respond(HttpStatusCode.OK, success(response))
                
            } catch (e: Exception) {
                Napier.e("Failed to begin account creation", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to begin account creation")
                )
            }
        }
        
        // Complete account creation with passkey
        post("/create/complete") {
            try {
                val request = call.receive<CompleteAccountCreationRequest>()
                
                // Validate session
                val session = sessionManager.validateSession(request.sessionId, SessionType.ACCOUNT_CREATION)
                if (session == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_SESSION", "Invalid or expired session")
                    )
                    return@post
                }
                
                // Verify WebAuthn registration
                val verificationResult = webAuthnService.verifyRegistration(
                    userId = session.temporaryUserId,
                    challenge = session.challenge,
                    registrationResponse = request.credential
                )
                
                if (!verificationResult.success) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("REGISTRATION_FAILED", verificationResult.error ?: "Failed to verify passkey")
                    )
                    return@post
                }
                
                // Create permanent account
                val accountId = Uuid.random()
                val now = Clock.System.now()
                val account = Account(
                    id = accountId,
                    username = session.username,
                    displayName = session.username, // We'll use username as display name for now
                    createdAt = now,
                    lastSignInAt = now,
                    isActive = true
                )
                
                // Save account
                val savedAccount = accountRepository.save(account)
                
                // Mark session as used
                sessionManager.markSessionUsed(request.sessionId)
                
                // Generate tokens
                val accessToken = tokenService.generateAccessToken(accountId.toString())
                val refreshToken = tokenService.generateRefreshToken(accountId.toString())
                
                val response = CompleteAccountCreationResponse(
                    success = true,
                    account = AccountInfo(
                        userId = savedAccount.id,
                        username = savedAccount.username,
                        displayName = savedAccount.displayName,
                        createdAt = savedAccount.createdAt,
                        lastSignInAt = savedAccount.lastSignInAt
                    ),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    passkey = verificationResult.passkey!!
                )
                
                call.respond(HttpStatusCode.Created, success(response))
                
            } catch (e: Exception) {
                Napier.e("Failed to complete account creation", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to complete account creation")
                )
            }
        }
        
        // Begin authentication with passkey
        post("/auth/begin") {
            try {
                val request = call.receive<BeginAuthenticationRequest>()
                
                // Find user if username provided
                val user = if (request.username != null) {
                    accountRepository.findByUsername(request.username)
                } else null
                
                // Generate authentication options
                val authenticationOptions = webAuthnService.generateAuthenticationOptions(
                    userId = user?.id,
                    allowedCredentials = user?.let { webAuthnService.getUserCredentials(it.id) } ?: emptyList()
                )
                
                // Create session
                val session = sessionManager.createAuthenticationSession(
                    challenge = authenticationOptions.challenge,
                    accountHint = request.username
                )
                
                val response = BeginAuthenticationResponse(
                    sessionId = session.id,
                    challenge = authenticationOptions.challenge,
                    authenticationOptions = authenticationOptions
                )
                
                call.respond(HttpStatusCode.OK, success(response))
                
            } catch (e: Exception) {
                Napier.e("Failed to begin authentication", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to begin authentication")
                )
            }
        }
        
        // Complete authentication with passkey
        post("/auth/complete") {
            try {
                val request = call.receive<CompleteAuthenticationRequest>()
                
                // Validate session
                val session = sessionManager.validateSession(request.sessionId, SessionType.AUTHENTICATION)
                if (session == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_SESSION", "Invalid or expired session")
                    )
                    return@post
                }
                
                // Verify WebAuthn authentication
                val verificationResult = webAuthnService.verifyAuthentication(
                    challenge = session.challenge,
                    authenticationResponse = request.credential
                )
                
                if (!verificationResult.success || verificationResult.userId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        error("AUTHENTICATION_FAILED", verificationResult.error ?: "Failed to verify passkey")
                    )
                    return@post
                }
                
                // Get account
                val account = accountRepository.findById(verificationResult.userId!!)
                if (account == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        error("ACCOUNT_NOT_FOUND", "Account not found")
                    )
                    return@post
                }
                
                // Update last sign-in time
                accountRepository.updateLastSignIn(account.id)
                
                // Mark session as used
                sessionManager.markSessionUsed(request.sessionId)
                
                // Generate tokens
                val accessToken = tokenService.generateAccessToken(account.id.toString())
                val refreshToken = tokenService.generateRefreshToken(account.id.toString())
                
                val response = CompleteAuthenticationResponse(
                    success = true,
                    account = AccountInfo(
                        userId = account.id,
                        username = account.username,
                        displayName = account.displayName,
                        createdAt = account.createdAt,
                        lastSignInAt = account.lastSignInAt
                    ),
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
                
                call.respond(HttpStatusCode.OK, success(response))
                
            } catch (e: Exception) {
                Napier.e("Failed to complete authentication", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to complete authentication")
                )
            }
        }
        
        // Refresh access token
        post("/token/refresh") {
            try {
                val authHeader = call.request.headers["Authorization"]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        error("MISSING_TOKEN", "Refresh token required")
                    )
                    return@post
                }
                
                val refreshToken = authHeader.substring(7)
                val accountId = tokenService.validateRefreshToken(refreshToken)
                
                if (accountId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        error("INVALID_TOKEN", "Invalid or expired refresh token")
                    )
                    return@post
                }
                
                // Generate new access token
                val newAccessToken = tokenService.generateAccessToken(accountId)
                
                call.respond(
                    HttpStatusCode.OK,
                    success(mapOf("accessToken" to newAccessToken))
                )
                
            } catch (e: Exception) {
                Napier.e("Failed to refresh token", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to refresh token")
                )
            }
        }
        
        // Get current account info
        get("/me") {
            try {
                val authHeader = call.request.headers["Authorization"]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        error("MISSING_TOKEN", "Access token required")
                    )
                    return@get
                }
                
                val accessToken = authHeader.substring(7)
                val accountId = tokenService.validateAccessToken(accessToken)
                
                if (accountId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        error("INVALID_TOKEN", "Invalid or expired access token")
                    )
                    return@get
                }
                
                val account = accountRepository.findById(Uuid.parse(accountId))
                if (account == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        error("ACCOUNT_NOT_FOUND", "Account not found")
                    )
                    return@get
                }
                
                val passkeys = webAuthnService.getPasskeysForUser(account.id)
                
                val response = mapOf(
                    "account" to AccountInfo(
                        userId = account.id,
                        username = account.username,
                        displayName = account.displayName,
                        createdAt = account.createdAt,
                        lastSignInAt = account.lastSignInAt
                    ),
                    "passkeys" to passkeys
                )
                
                call.respond(HttpStatusCode.OK, success(response))
                
            } catch (e: Exception) {
                Napier.e("Failed to get account info", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    error("SERVER_ERROR", "Failed to get account info")
                )
            }
        }
        
        // Update account preferences (stub for now)
        patch("/me") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Account update not implemented yet")
            )
        }
        
        // Deactivate account (stub for now)
        delete("/me") {
            call.respond(
                HttpStatusCode.NotImplemented,
                error("NOT_IMPLEMENTED", "Account deletion not implemented yet")
            )
        }
    }
}