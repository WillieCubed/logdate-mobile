package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.SessionManager
import app.logdate.server.auth.SessionType
import app.logdate.server.auth.TokenService
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.util.toKotlinInstant
import app.logdate.shared.model.*
import io.github.aakira.napier.Napier
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.time.Clock
import kotlinx.serialization.SerializationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val USERNAME_MIN_LENGTH = 3
private const val USERNAME_MAX_LENGTH = 50
private const val DISPLAY_NAME_MAX_LENGTH = 100
private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]+$")

@OptIn(ExperimentalUuidApi::class)
fun Route.accountRoutes(
    accountRepository: AccountRepository,
    sessionManager: SessionManager,
    webAuthnService: WebAuthnPasskeyService,
    tokenService: TokenService
) {
    route("/accounts") {
        // Check username availability
        get("/username/{username}/available") {
            try {
                val username = call.parameters["username"]?.trim().orEmpty()
                val validationError = validateUsername(username)

                if (validationError != null) {
                    call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", validationError)
                    return@get
                }

                val isAvailable = !accountRepository.usernameExists(username)
                call.respond(
                    HttpStatusCode.OK,
                    UsernameAvailabilityResponse(
                        success = true,
                        data = UsernameAvailabilityData(
                            username = username,
                            available = isAvailable
                        )
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to check username availability", e)
                call.respondForRequestException(e, "Failed to check username availability")
            }
        }

        // Begin account creation with passkey
        post("/create/begin") {
            try {
                val request = call.receive<BeginAccountCreationRequest>()

                val usernameError = validateUsername(request.username)
                if (usernameError != null) {
                    call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", usernameError)
                    return@post
                }

                val displayNameError = validateDisplayName(request.displayName)
                if (displayNameError != null) {
                    call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", displayNameError)
                    return@post
                }

                if (accountRepository.usernameExists(request.username)) {
                    call.respondApiError(
                        HttpStatusCode.Conflict,
                        "USERNAME_TAKEN",
                        "Username is already taken"
                    )
                    return@post
                }

                val temporaryUserId = Uuid.random()
                val registrationOptions = webAuthnService.generateRegistrationOptions(
                    userId = temporaryUserId,
                    username = request.username,
                    displayName = request.displayName
                )

                val session = sessionManager.createAccountCreationSession(
                    temporaryUserId = temporaryUserId,
                    username = request.username,
                    displayName = request.displayName,
                    challenge = registrationOptions.challenge,
                    deviceInfo = null,
                    bio = request.bio
                )

                call.respond(
                    HttpStatusCode.OK,
                    BeginAccountCreationResponse(
                        success = true,
                        data = BeginAccountCreationData(
                            sessionToken = session.id,
                            registrationOptions = registrationOptions
                        )
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to begin account creation", e)
                call.respondForRequestException(e, "Failed to begin account creation")
            }
        }

        // Complete account creation with passkey
        post("/create/complete") {
            try {
                val request = call.receive<CompleteAccountCreationRequest>()

                val session = sessionManager.validateSession(request.sessionToken, SessionType.ACCOUNT_CREATION)
                if (session == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_SESSION_TOKEN",
                        "Session token is invalid or expired"
                    )
                    return@post
                }

                val registrationResponse = request.credential.toPasskeyRegistrationResponse()
                val verificationResult = webAuthnService.verifyRegistration(
                    userId = session.temporaryUserId,
                    challenge = session.challenge,
                    registrationResponse = registrationResponse
                )

                if (!verificationResult.success) {
                    call.respondApiError(
                        HttpStatusCode.BadRequest,
                        "PASSKEY_VERIFICATION_FAILED",
                        verificationResult.error ?: "Failed to verify passkey"
                    )
                    return@post
                }

                val now = Clock.System.now()
                val accountId = Uuid.random()
                val account = Account(
                    id = accountId,
                    username = session.username,
                    displayName = session.displayName,
                    bio = session.bio,
                    createdAt = now,
                    lastSignInAt = now,
                    isActive = true
                )

                val savedAccount = accountRepository.save(account)
                sessionManager.markSessionUsed(request.sessionToken)

                val accessToken = tokenService.generateAccessToken(accountId.toString())
                val refreshToken = tokenService.generateRefreshToken(accountId.toString())
                val passkeyIds = webAuthnService.getPasskeysForUser(savedAccount.id).map { it.credentialId }

                call.respond(
                    HttpStatusCode.Created,
                    CompleteAccountCreationResponse(
                        success = true,
                        data = CompleteAccountCreationData(
                            account = savedAccount.toLogDateAccount(passkeyIds),
                            tokens = AccountTokens(
                                accessToken = accessToken,
                                refreshToken = refreshToken
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to complete account creation", e)
                call.respondForRequestException(e, "Failed to complete account creation")
            }
        }

        // Begin authentication with passkey
        post("/authenticate/begin") {
            try {
                val request = call.receive<BeginAuthenticationRequest>()

                val account = request.username?.let { accountRepository.findByUsername(it) }
                val allowCredentials = account?.let { webAuthnService.getUserCredentials(it.id) } ?: emptyList()

                val authOptions = webAuthnService.generateAuthenticationOptions(
                    userId = account?.id,
                    allowedCredentials = allowCredentials
                )

                val response = BeginAuthenticationResponse(
                    success = true,
                    data = BeginAuthenticationData(
                        challenge = authOptions.challenge,
                        rpId = webAuthnService.relyingPartyId,
                        allowCredentials = authOptions.allowCredentials.map {
                            PasskeyAllowCredential(id = it, transports = emptyList())
                        },
                        timeout = authOptions.timeout,
                        userVerification = "required"
                    )
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                Napier.e("Failed to begin authentication", e)
                call.respondForRequestException(e, "Failed to begin authentication")
            }
        }

        // Complete authentication with passkey
        post("/authenticate/complete") {
            try {
                val request = call.receive<CompleteAuthenticationRequest>()

                val verificationResult = webAuthnService.verifyAuthentication(
                    challenge = request.challenge,
                    authenticationResponse = request.credential.toPasskeyAuthenticationResponse()
                )

                if (!verificationResult.success || verificationResult.userId == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "AUTHENTICATION_FAILED",
                        verificationResult.error ?: "Failed to verify passkey"
                    )
                    return@post
                }

                val account = accountRepository.findById(verificationResult.userId)
                if (account == null) {
                    call.respondApiError(
                        HttpStatusCode.NotFound,
                        "ACCOUNT_NOT_FOUND",
                        "Account not found"
                    )
                    return@post
                }

                accountRepository.updateLastSignIn(account.id)

                val accessToken = tokenService.generateAccessToken(account.id.toString())
                val refreshToken = tokenService.generateRefreshToken(account.id.toString())
                val passkeyIds = webAuthnService.getUserCredentials(account.id)

                call.respond(
                    HttpStatusCode.OK,
                    CompleteAuthenticationResponse(
                        success = true,
                        data = CompleteAuthenticationData(
                            account = account.toLogDateAccount(passkeyIds),
                            tokens = AccountTokens(
                                accessToken = accessToken,
                                refreshToken = refreshToken
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to complete authentication", e)
                call.respondForRequestException(e, "Failed to complete authentication")
            }
        }

        // Refresh access token
        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                if (request.refreshToken.isBlank()) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is required"
                    )
                    return@post
                }

                val accountId = tokenService.validateRefreshToken(request.refreshToken)
                if (accountId == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_REFRESH_TOKEN",
                        "Invalid or expired refresh token"
                    )
                    return@post
                }

                val newAccessToken = tokenService.generateAccessToken(accountId)
                call.respond(
                    HttpStatusCode.OK,
                    RefreshTokenResponse(
                        success = true,
                        data = RefreshTokenData(accessToken = newAccessToken)
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to refresh token", e)
                call.respondForRequestException(e, "Failed to refresh token")
            }
        }

        // Get current account info
        get("/me") {
            try {
                val accessToken = call.extractBearerToken()
                if (accessToken == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Access token is required"
                    )
                    return@get
                }

                val accountId = tokenService.validateAccessToken(accessToken)
                if (accountId == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Invalid or expired access token"
                    )
                    return@get
                }

                val account = accountRepository.findById(Uuid.parse(accountId))
                if (account == null) {
                    call.respondApiError(
                        HttpStatusCode.NotFound,
                        "ACCOUNT_NOT_FOUND",
                        "Account not found"
                    )
                    return@get
                }

                val passkeyIds = webAuthnService.getPasskeysForUser(account.id).map { it.credentialId }
                call.respond(
                    HttpStatusCode.OK,
                    AccountInfoResponse(
                        success = true,
                        data = account.toLogDateAccount(passkeyIds)
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to get account info", e)
                call.respondForRequestException(e, "Failed to get account info")
            }
        }

        // Update account profile
        put("/me") {
            try {
                val accessToken = call.extractBearerToken()
                if (accessToken == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Access token is required"
                    )
                    return@put
                }

                val accountId = tokenService.validateAccessToken(accessToken)
                if (accountId == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Invalid or expired access token"
                    )
                    return@put
                }

                val account = accountRepository.findById(Uuid.parse(accountId))
                if (account == null) {
                    call.respondApiError(
                        HttpStatusCode.NotFound,
                        "ACCOUNT_NOT_FOUND",
                        "Account not found"
                    )
                    return@put
                }

                val request = call.receive<UpdateAccountProfileRequest>()
                if (request.displayName == null && request.username == null && request.bio == null) {
                    call.respondApiError(
                        HttpStatusCode.BadRequest,
                        "VALIDATION_ERROR",
                        "At least one field must be provided"
                    )
                    return@put
                }

                request.username?.let {
                    val usernameError = validateUsername(it)
                    if (usernameError != null) {
                        call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", usernameError)
                        return@put
                    }

                    if (!it.equals(account.username, ignoreCase = false) && accountRepository.usernameExists(it)) {
                        call.respondApiError(
                            HttpStatusCode.Conflict,
                            "USERNAME_TAKEN",
                            "Username is already taken"
                        )
                        return@put
                    }
                }

                request.displayName?.let {
                    val displayNameError = validateDisplayName(it)
                    if (displayNameError != null) {
                        call.respondApiError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", displayNameError)
                        return@put
                    }
                }

                val updatedAccount = account.copy(
                    username = request.username ?: account.username,
                    displayName = request.displayName ?: account.displayName,
                    bio = request.bio ?: account.bio
                )

                val savedAccount = accountRepository.save(updatedAccount)
                val passkeyIds = webAuthnService.getPasskeysForUser(savedAccount.id).map { it.credentialId }

                call.respond(
                    HttpStatusCode.OK,
                    AccountInfoResponse(
                        success = true,
                        data = savedAccount.toLogDateAccount(passkeyIds)
                    )
                )
            } catch (e: Exception) {
                Napier.e("Failed to update account profile", e)
                call.respondForRequestException(e, "Failed to update account profile")
            }
        }

        // Delete a passkey credential
        delete("/me/passkeys/{credentialId}") {
            try {
                val accessToken = call.extractBearerToken()
                if (accessToken == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Access token is required"
                    )
                    return@delete
                }

                val accountId = tokenService.validateAccessToken(accessToken)
                if (accountId == null) {
                    call.respondApiError(
                        HttpStatusCode.Unauthorized,
                        "INVALID_TOKEN",
                        "Invalid or expired access token"
                    )
                    return@delete
                }

                val credentialId = call.parameters["credentialId"]?.trim().orEmpty()
                if (credentialId.isBlank()) {
                    call.respondApiError(
                        HttpStatusCode.BadRequest,
                        "VALIDATION_ERROR",
                        "Credential ID is required"
                    )
                    return@delete
                }

                val account = accountRepository.findById(Uuid.parse(accountId))
                if (account == null) {
                    call.respondApiError(
                        HttpStatusCode.NotFound,
                        "ACCOUNT_NOT_FOUND",
                        "Account not found"
                    )
                    return@delete
                }

                val deleted = webAuthnService.deletePasskey(credentialId, account.id)
                if (!deleted) {
                    call.respondApiError(
                        HttpStatusCode.NotFound,
                        "PASSKEY_NOT_FOUND",
                        "Passkey not found"
                    )
                    return@delete
                }

                call.respond(HttpStatusCode.NoContent)
            } catch (e: Exception) {
                Napier.e("Failed to delete passkey", e)
                call.respondForRequestException(e, "Failed to delete passkey")
            }
        }
    }
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

@OptIn(ExperimentalUuidApi::class)
private fun Account.toLogDateAccount(passkeyCredentialIds: List<String>): LogDateAccount {
    return LogDateAccount(
        id = id,
        username = username,
        displayName = displayName,
        bio = bio,
        passkeyCredentialIds = passkeyCredentialIds,
        createdAt = createdAt.toKotlinInstant(),
        updatedAt = (lastSignInAt ?: createdAt).toKotlinInstant()
    )
}

private fun ApplicationCall.extractBearerToken(): String? {
    val authHeader = request.headers["Authorization"] ?: return null
    if (!authHeader.startsWith("Bearer ")) {
        return null
    }

    return authHeader.removePrefix("Bearer ").trim().ifBlank { null }
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    code: String,
    message: String
) {
    respond(status, ApiErrorResponse(ApiError(code, message)))
}

private suspend fun ApplicationCall.respondForRequestException(
    error: Exception,
    fallbackMessage: String
) {
    when (error) {
        is ContentTransformationException,
        is ContentConvertException,
        is SerializationException,
        is BadRequestException -> respondApiError(
            HttpStatusCode.BadRequest,
            "INVALID_REQUEST",
            "Request body is invalid"
        )
        else -> respondApiError(
            HttpStatusCode.InternalServerError,
            "SERVER_ERROR",
            fallbackMessage
        )
    }
}

private fun PasskeyCredentialResponse.toPasskeyRegistrationResponse(): PasskeyRegistrationResponse {
    return PasskeyRegistrationResponse(
        id = id,
        rawId = rawId,
        response = AuthenticatorAttestationResponse(
            clientDataJSON = response.clientDataJSON,
            attestationObject = response.attestationObject
        ),
        type = type
    )
}

private fun PasskeyAssertionResponse.toPasskeyAuthenticationResponse(): PasskeyAuthenticationResponse {
    return PasskeyAuthenticationResponse(
        id = id,
        rawId = rawId,
        response = AuthenticatorAssertionResponse(
            clientDataJSON = response.clientDataJSON,
            authenticatorData = response.authenticatorData,
            signature = response.signature,
            userHandle = response.userHandle
        ),
        type = type
    )
}
