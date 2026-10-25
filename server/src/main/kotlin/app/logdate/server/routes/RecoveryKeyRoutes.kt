package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.TokenService
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.IdentityAccountMissingException
import app.logdate.server.identity.IdentityLifecycleConflictException
import app.logdate.server.identity.IdentityLifecycleValidationException
import app.logdate.server.identity.RegisteredPlcRecoveryKey
import app.logdate.server.identity.didKeyFor
import app.logdate.server.passkeys.PasskeyStepUp
import app.logdate.server.passkeys.PasskeyStepUpProof
import app.logdate.server.routes.docs.RecoveryKeyDocs
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi

@Serializable
data class RegisterPlcRecoveryKeyRequest(
    val recoveryDidKey: String,
    /** A fresh passkey confirmation from **Confirm a recovery-key change**. */
    val stepUp: PasskeyStepUpProof? = null,
    /**
     * The `stepUpGrant` from a passkey sign-in or sign-up in the last five minutes, used once in
     * place of [stepUp]. Only registering the first recovery key accepts it; replacing one always
     * needs a fresh confirmation.
     */
    val stepUpGrant: String? = null,
)

@Serializable
data class RegisterPlcRecoveryKeyData(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
)

@Serializable
data class RegisterPlcRecoveryKeyResponse(
    val success: Boolean,
    val data: RegisterPlcRecoveryKeyData,
)

@Serializable
data class RecoveryKeyStepUpResponse(
    val success: Boolean,
    val data: SigninPasskeyBeginData,
)

@Serializable
data class PrepareIdentityRecoveryData(
    val did: String,
    val handle: String,
    val recoveryDidKey: String,
    val nextPublicKeyDidKey: String,
    val unsignedOperationJson: String,
    val signingPayloadBase64Url: String,
    val preparedKey: String,
)

@Serializable
data class PrepareIdentityRecoveryResponse(
    val success: Boolean,
    val data: PrepareIdentityRecoveryData,
)

@Serializable
data class CompleteIdentityRecoveryRequest(
    val preparedKey: String,
    val signature: String,
    /** A fresh passkey confirmation from **Confirm a recovery-key change**. */
    val stepUp: PasskeyStepUpProof? = null,
)

/**
 * The recovery-key endpoints under `/identity`: registering and replacing the person's recovery key,
 * and using it to replace the server's key. Every change needs a fresh passkey confirmation from
 * [passkeyStepUp], so a stolen session token alone cannot make one; without [passkeyStepUp] they
 * are refused.
 */
internal fun Route.recoveryKeyRoutes(
    accountRepository: AccountRepository,
    tokenService: TokenService,
    atprotoIdentityService: AtprotoIdentityService,
    passkeyStepUp: PasskeyStepUp?,
) {
    val routes = RecoveryKeyRouteContext(accountRepository, tokenService, atprotoIdentityService, passkeyStepUp)
    stepUpRoute(routes)
    recoveryKeyChangeRoute(
        routes,
        "/plc/recovery-key",
        RecoveryKeyDocs.registerPlcRecoveryKey,
        "register the recovery key",
        acceptsGrant = true,
    ) {
        account,
        key,
        ->
        atprotoIdentityService.registerPlcRecoveryKey(account, key)
    }
    recoveryKeyChangeRoute(routes, "/plc/recovery-key/replace", RecoveryKeyDocs.replacePlcRecoveryKey, "replace the recovery key") {
        account,
        key,
        ->
        atprotoIdentityService.replacePlcRecoveryKey(account, key)
    }
    prepareRecoveryRoute(routes)
    completeRecoveryRoute(routes)
}

private class RecoveryKeyRouteContext(
    private val accountRepository: AccountRepository,
    private val tokenService: TokenService,
    val identity: AtprotoIdentityService,
    val passkeyStepUp: PasskeyStepUp?,
) {
    suspend fun account(call: ApplicationCall): Account? = resolveIdentityApiAccount(call, accountRepository, tokenService)
}

@OptIn(ExperimentalUuidApi::class)
private fun Route.stepUpRoute(routes: RecoveryKeyRouteContext) {
    post("/recovery-key/step-up", RecoveryKeyDocs.beginRecoveryKeyStepUp) {
        val account = routes.account(call) ?: return@post
        call.handleRecoveryKeyFailures("begin a recovery-key confirmation") {
            if (!call.recoveryKeysAvailable(routes.identity)) return@handleRecoveryKeyFailures
            val stepUp = routes.passkeyStepUp ?: return@handleRecoveryKeyFailures call.respondStepUpUnavailable()
            if (stepUp.credentialsFor(account.id).isEmpty()) {
                return@handleRecoveryKeyFailures call.respondIdentityApiError(
                    HttpStatusCode.Conflict,
                    PASSKEY_REQUIRED,
                    "Add a passkey to this account before changing its recovery key",
                )
            }
            val options = stepUp.begin(account.id)
            call.respond(
                HttpStatusCode.OK,
                RecoveryKeyStepUpResponse(
                    success = true,
                    data =
                        SigninPasskeyBeginData(
                            challenge = options.challenge,
                            rpId = options.rpId,
                            allowCredentials = options.allowCredentials.map { PasskeyAllowCredentialDto(id = it) },
                            timeout = options.timeout,
                            userVerification = "required",
                        ),
                ),
            )
        }
    }
}

/** Registering and replacing share one shape: a `did:key`, a step-up, then the [change]. */
private fun Route.recoveryKeyChangeRoute(
    routes: RecoveryKeyRouteContext,
    path: String,
    docs: RouteConfig.() -> Unit,
    operation: String,
    acceptsGrant: Boolean = false,
    change: suspend (Account, String) -> RegisteredPlcRecoveryKey,
) {
    post(path, docs) {
        val account = routes.account(call) ?: return@post
        call.handleRecoveryKeyFailures(operation) {
            val request = call.receive<RegisterPlcRecoveryKeyRequest>()
            if (request.recoveryDidKey.isBlank()) {
                return@handleRecoveryKeyFailures call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    "RECOVERY_DID_KEY_REQUIRED",
                    "Recovery did:key is required",
                )
            }
            if (routes.identity.canonicalRecoveryDidKey(request.recoveryDidKey) == null) {
                return@handleRecoveryKeyFailures call.respondIdentityApiError(
                    HttpStatusCode.BadRequest,
                    RECOVERY_KEY_INVALID,
                    "Recovery key must be a valid did:key multikey value",
                )
            }
            if (!call.recoveryKeysAvailable(routes.identity)) return@handleRecoveryKeyFailures
            val grant = request.stepUpGrant.takeIf { acceptsGrant && request.stepUp == null }
            if (!call.confirmStepUp(routes.passkeyStepUp, account, request.stepUp, grant)) return@handleRecoveryKeyFailures
            val registered = change(account, request.recoveryDidKey)
            call.respond(
                HttpStatusCode.OK,
                RegisterPlcRecoveryKeyResponse(
                    success = true,
                    data =
                        RegisterPlcRecoveryKeyData(
                            did = requireNotNull(registered.account.did),
                            handle = requireNotNull(registered.account.handle),
                            recoveryDidKey = registered.recoveryDidKey,
                        ),
                ),
            )
        }
    }
}

private fun Route.prepareRecoveryRoute(routes: RecoveryKeyRouteContext) {
    val plcJson =
        Json {
            encodeDefaults = true
            explicitNulls = true
        }
    post("/recovery-key/recover/prepare", RecoveryKeyDocs.prepareIdentityRecovery) {
        val account = routes.account(call) ?: return@post
        call.handleRecoveryKeyFailures("prepare identity recovery") {
            val prepared = routes.identity.prepareRecovery(account)
            call.respond(
                HttpStatusCode.OK,
                PrepareIdentityRecoveryResponse(
                    success = true,
                    data =
                        PrepareIdentityRecoveryData(
                            did = prepared.did,
                            handle = prepared.handle,
                            recoveryDidKey = prepared.recoveryDidKey,
                            nextPublicKeyDidKey = prepared.nextSigningKeyDidKey,
                            unsignedOperationJson = plcJson.encodeToString(prepared.unsignedOperation),
                            signingPayloadBase64Url = prepared.signingPayloadBase64Url,
                            preparedKey = prepared.preparedKey,
                        ),
                ),
            )
        }
    }
}

private fun Route.completeRecoveryRoute(routes: RecoveryKeyRouteContext) {
    post("/recovery-key/recover/complete", RecoveryKeyDocs.completeIdentityRecovery) {
        val account = routes.account(call) ?: return@post
        call.handleRecoveryKeyFailures("complete identity recovery") {
            val request = call.receive<CompleteIdentityRecoveryRequest>()
            val missing =
                when {
                    request.preparedKey.isBlank() -> "PREPARED_KEY_REQUIRED" to "Prepared key is required"
                    request.signature.isBlank() -> "SIGNATURE_REQUIRED" to "Recovery signature is required"
                    else -> null
                }
            if (missing != null) {
                return@handleRecoveryKeyFailures call.respondIdentityApiError(HttpStatusCode.BadRequest, missing.first, missing.second)
            }
            if (!call.recoveryKeysAvailable(routes.identity)) return@handleRecoveryKeyFailures
            if (!call.confirmStepUp(routes.passkeyStepUp, account, request.stepUp)) return@handleRecoveryKeyFailures

            val recovered = routes.identity.completeRecovery(account, request.preparedKey, request.signature)
            call.respond(
                HttpStatusCode.OK,
                ImportSigningKeyResponse(
                    success = true,
                    data =
                        ImportSigningKeyData(
                            did = requireNotNull(recovered.account.did),
                            handle = requireNotNull(recovered.account.handle),
                            publicKeyDidKey = didKeyFor(recovered.activeKey.publicKeyMultibase),
                        ),
                ),
            )
        }
    }
}

/**
 * Answers a recovery-key request's failure with its specific code: `400` for a request that can
 * never succeed as sent, `409` for the identity's state, `404` when the account was deleted
 * meanwhile, and `500 RECOVERY_KEY_FAILED` for anything else.
 */
private suspend fun ApplicationCall.handleRecoveryKeyFailures(
    operation: String,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IdentityLifecycleValidationException) {
        respondIdentityApiError(HttpStatusCode.BadRequest, error.code ?: RECOVERY_KEY_INVALID, error.message ?: "Invalid request")
    } catch (error: IdentityLifecycleConflictException) {
        respondIdentityApiError(HttpStatusCode.Conflict, error.code ?: RECOVERY_KEY_CONFLICT, error.message ?: "Identity state conflict")
    } catch (_: IdentityAccountMissingException) {
        respondIdentityApiError(HttpStatusCode.NotFound, "ACCOUNT_NOT_FOUND", "Account not found")
    } catch (error: Exception) {
        Napier.e("Failed to $operation", error)
        respondIdentityApiError(HttpStatusCode.InternalServerError, RECOVERY_KEY_FAILED, "Failed to $operation")
    }
}

/**
 * Checks the request's passkey step-up, or the sign-in [grant] a route accepts in its place, and
 * answers the failure when it does not hold: `409 STEP_UP_UNAVAILABLE` when this server cannot
 * confirm with a passkey, `403 STEP_UP_REQUIRED` when none was sent, `403 STEP_UP_FAILED` when it
 * was not accepted.
 */
@OptIn(ExperimentalUuidApi::class)
private suspend fun ApplicationCall.confirmStepUp(
    passkeyStepUp: PasskeyStepUp?,
    account: Account,
    proof: PasskeyStepUpProof?,
    grant: String? = null,
): Boolean {
    if (passkeyStepUp == null) {
        respondStepUpUnavailable()
        return false
    }
    if (proof == null && grant != null) {
        if (passkeyStepUp.redeem(account.id, grant)) return true
        respondIdentityApiError(HttpStatusCode.Forbidden, "STEP_UP_FAILED", "The passkey confirmation was not accepted")
        return false
    }
    if (proof == null) {
        respondIdentityApiError(HttpStatusCode.Forbidden, "STEP_UP_REQUIRED", "Confirm this change with a passkey first")
        return false
    }
    if (!passkeyStepUp.verify(account.id, proof)) {
        respondIdentityApiError(HttpStatusCode.Forbidden, "STEP_UP_FAILED", "The passkey confirmation was not accepted")
        return false
    }
    return true
}

/** Answers `409 RECOVERY_KEYS_UNAVAILABLE` before a step-up is spent on a change this server cannot make. */
private suspend fun ApplicationCall.recoveryKeysAvailable(atprotoIdentityService: AtprotoIdentityService): Boolean {
    if (atprotoIdentityService.config.supportsRecoveryKeys) return true
    respondIdentityApiError(HttpStatusCode.Conflict, RECOVERY_KEYS_UNAVAILABLE, "Recovery keys are not enabled on this server")
    return false
}

private suspend fun ApplicationCall.respondStepUpUnavailable() =
    respondIdentityApiError(HttpStatusCode.Conflict, "STEP_UP_UNAVAILABLE", "This server cannot confirm changes with a passkey")

private const val RECOVERY_KEYS_UNAVAILABLE = "RECOVERY_KEYS_UNAVAILABLE"
private const val PASSKEY_REQUIRED = "PASSKEY_REQUIRED"
private const val RECOVERY_KEY_INVALID = "PLC_RECOVERY_KEY_INVALID"
private const val RECOVERY_KEY_CONFLICT = "PLC_RECOVERY_KEY_CONFLICT"
private const val RECOVERY_KEY_FAILED = "PLC_RECOVERY_KEY_FAILED"
