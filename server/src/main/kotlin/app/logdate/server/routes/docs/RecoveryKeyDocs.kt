package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.passkeys.PasskeyStepUpProof
import app.logdate.server.routes.CompleteIdentityRecoveryRequest
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.ImportSigningKeyData
import app.logdate.server.routes.ImportSigningKeyResponse
import app.logdate.server.routes.PasskeyAllowCredentialDto
import app.logdate.server.routes.PrepareIdentityRecoveryData
import app.logdate.server.routes.PrepareIdentityRecoveryResponse
import app.logdate.server.routes.RecoveryKeyStepUpResponse
import app.logdate.server.routes.RegisterPlcRecoveryKeyData
import app.logdate.server.routes.RegisterPlcRecoveryKeyRequest
import app.logdate.server.routes.RegisterPlcRecoveryKeyResponse
import app.logdate.server.routes.SigninPasskeyBeginData
import app.logdate.server.routes.apiError
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/**
 * Documentation for `RecoveryKeyRoutes.kt`: registering and replacing the recovery key a person holds
 * for their identity, the passkey confirmation those changes need, and replacing the server's key.
 */
internal object RecoveryKeyDocs {
    private const val NEXT_DID_KEY = "did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"
    private const val RECOVERY_DID_KEY = "did:key:zQ3shbgnTtF5t6yKw3KwDmgb4iFdGb3dUDbKsGxTMVaMdumoT"

    private val stepUpExample = PasskeyStepUpProof(challenge = DocExamples.CHALLENGE, credential = passkeyAssertion)

    private val recoveryKeysUnavailable =
        ErrorCase(
            "RECOVERY_KEYS_UNAVAILABLE",
            "This server does not support recovery keys (it does not advertise `atprotoRecoveryKeysV1`), or the " +
                "identity is not a `did:plc`. Nothing was changed.",
            "Recovery keys are not enabled on this server",
        )

    private val recoveryKeyNotSet =
        ErrorCase(
            "RECOVERY_KEY_NOT_SET",
            "No recovery key is registered for this identity. Register one with **Register a recovery key** first.",
            "No recovery key is registered for this identity",
        )

    private val recoveryKeyNotOnIdentity =
        ErrorCase(
            "RECOVERY_KEY_NOT_ON_IDENTITY",
            "The identity's latest operation does not list the registered recovery key, so it cannot sign the next one. " +
                "Put a key back with **Replace the recovery key**.",
            "The identity's latest operation does not list the registered recovery key",
        )

    private val serverKeyNotOnIdentity =
        ErrorCase(
            "SERVER_KEY_NOT_ON_IDENTITY",
            "The identity's latest operation does not list the server's current key, so the server cannot sign the change. " +
                "Nothing was changed. The operator has to repair the identity's history.",
            "The identity's latest operation does not list the server's current key",
        )

    private val identityChanged =
        ErrorCase(
            "IDENTITY_CHANGED",
            "Another change to the identity was made at the same time, or after step one. Nothing was changed by this request. " +
                "Start again.",
            "The identity changed while this request was in progress; start again",
        )

    private val stepUpRequired =
        ErrorCase(
            "STEP_UP_REQUIRED",
            "`stepUp` is missing. Get a challenge from **Confirm a recovery-key change**, answer it with a passkey, and send it.",
            "Confirm this change with a passkey first",
        )

    private val stepUpFailed =
        ErrorCase(
            "STEP_UP_FAILED",
            "The passkey confirmation was not accepted: the challenge was not issued to this account, was used already, " +
                "is more than five minutes old, or the assertion does not verify. Get a new challenge and try again.",
            "The passkey confirmation was not accepted",
        )

    private val stepUpUnavailable =
        ErrorCase(
            "STEP_UP_UNAVAILABLE",
            "This server cannot confirm changes with a passkey, so it refuses recovery-key changes. Nothing was changed.",
            "This server cannot confirm changes with a passkey",
        )

    private val recoveryKeyFailed =
        ErrorCase(
            "PLC_RECOVERY_KEY_FAILED",
            "Something failed on the server, or the body was not valid JSON for this request (that case answers `500` here, " +
                "not `400`). Check the body first; retry with backoff only if it is well-formed.",
            "Failed to change the recovery key",
        )

    private fun ResponsesConfig.stepUpFailures() {
        apiError(HttpStatusCode.Forbidden, stepUpRequired, stepUpFailed)
    }

    private fun ResponsesConfig.commonFailures() {
        bearerUnauthorized(ErrorEnvelope.API)
        apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
        apiError(HttpStatusCode.InternalServerError, recoveryKeyFailed)
    }

    val beginRecoveryKeyStepUp: RouteConfig.() -> Unit = {
        bearerOperation(
            "beginRecoveryKeyStepUp",
            ApiTags.IDENTITY,
            "Confirm a recovery-key change",
            """
            Registering or replacing a recovery key, and replacing the server's key, each need a fresh passkey
            confirmation as well as the session, so someone holding only a stolen access token cannot make
            them. This returns a challenge that only this account's passkeys can answer. Pass `data` to the
            platform's credential API, then send the challenge and the signed credential as `stepUp` with the
            change.

            The challenge is single-use, is spent whether or not the change succeeds, and expires after five
            minutes. It cannot be used to sign in.
            """,
        )
        response {
            ok(
                "A challenge for one of the account's passkeys to sign.",
                RecoveryKeyStepUpResponse(
                    success = true,
                    data =
                        SigninPasskeyBeginData(
                            challenge = DocExamples.CHALLENGE,
                            rpId = "logdate.app",
                            allowCredentials = listOf(PasskeyAllowCredentialDto(id = DocExamples.CREDENTIAL_ID)),
                            timeout = 300_000,
                            userVerification = "required",
                        ),
                ),
            )
            apiError(
                HttpStatusCode.Conflict,
                recoveryKeysUnavailable,
                stepUpUnavailable,
                ErrorCase(
                    "PASSKEY_REQUIRED",
                    "The account has no passkey, so it cannot confirm the change. Add a passkey first.",
                    "Add a passkey to this account before changing its recovery key",
                ),
            )
            commonFailures()
        }
    }

    val registerPlcRecoveryKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "registerPlcRecoveryKey",
            ApiTags.IDENTITY,
            "Register a recovery key",
            """
            Records a `did:key` the person controls as the identity's PLC recovery key. The server appends an
            operation that lists it first in `rotationKeys`, ahead of the server's own key. The operation is
            published to the PLC directory only when this server publishes; otherwise the server's operation
            log is the identity's history, and the server could still rewrite it. Derive or generate the key on
            the device and keep the private half there.

            Needs `stepUp` from **Confirm a recovery-key change**, or instead the `stepUpGrant` from a
            passkey sign-in or sign-up in the last five minutes, which is spent on first use. Registering the
            same key again changes nothing. A different key is refused while one is set; use **Replace the recovery key** for that.
            Requires the `atprotoRecoveryKeysV1` protocol feature.
            """,
        )
        request {
            jsonBody(
                RegisterPlcRecoveryKeyRequest(recoveryDidKey = RECOVERY_DID_KEY, stepUp = stepUpExample),
                "The public recovery key as a `did:key`, and the passkey confirmation.",
            )
        }
        response {
            ok("The recovery key is registered.", registeredExample)
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase("RECOVERY_DID_KEY_REQUIRED", "`recoveryDidKey` is blank.", "Recovery did:key is required"),
                ErrorCase(
                    "PLC_RECOVERY_KEY_INVALID",
                    "`recoveryDidKey` is not a valid `did:key` for a supported curve.",
                    "Recovery key must be a valid did:key multikey value",
                ),
            )
            stepUpFailures()
            apiError(
                HttpStatusCode.Conflict,
                recoveryKeysUnavailable,
                stepUpUnavailable,
                ErrorCase(
                    "RECOVERY_KEY_ALREADY_SET",
                    "A different recovery key is already registered. Nothing was changed. Use **Replace the recovery key**.",
                    "A different recovery key is already registered for this identity",
                ),
                serverKeyNotOnIdentity,
                identityChanged,
                ErrorCase(
                    "PLC_RECOVERY_KEY_CONFLICT",
                    "The identity's history could not be read. Nothing was changed.",
                    "Identity state conflict",
                ),
            )
            commonFailures()
        }
    }

    val replacePlcRecoveryKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "replacePlcRecoveryKey",
            ApiTags.IDENTITY,
            "Replace the recovery key",
            """
            Puts a new `did:key` in place of the registered recovery key, for someone who lost the phrase the
            old key came from. The server signs the change with its own key and lists the new key first in
            `rotationKeys`.

            Needs `stepUp` from **Confirm a recovery-key change**. Once the identity is published to the PLC
            directory, the old recovery key ranks above the server's key and can undo this change for 72
            hours after it is made. Replacing the key with the one already registered changes nothing.
            Requires the `atprotoRecoveryKeysV1` protocol feature.
            """,
        )
        request {
            jsonBody(
                RegisterPlcRecoveryKeyRequest(recoveryDidKey = RECOVERY_DID_KEY, stepUp = stepUpExample),
                "The new public recovery key as a `did:key`, and the passkey confirmation.",
            )
        }
        response {
            ok("The new recovery key is registered in place of the old one.", registeredExample)
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase("RECOVERY_DID_KEY_REQUIRED", "`recoveryDidKey` is blank.", "Recovery did:key is required"),
                ErrorCase(
                    "PLC_RECOVERY_KEY_INVALID",
                    "`recoveryDidKey` is not a valid `did:key` for a supported curve.",
                    "Recovery key must be a valid did:key multikey value",
                ),
            )
            stepUpFailures()
            apiError(
                HttpStatusCode.Conflict,
                recoveryKeysUnavailable,
                stepUpUnavailable,
                recoveryKeyNotSet,
                serverKeyNotOnIdentity,
                identityChanged,
            )
            commonFailures()
        }
    }

    private val registeredExample =
        RegisterPlcRecoveryKeyResponse(
            success = true,
            data = RegisterPlcRecoveryKeyData(did = DocExamples.DID, handle = DocExamples.HANDLE, recoveryDidKey = RECOVERY_DID_KEY),
        )

    val prepareIdentityRecovery: RouteConfig.() -> Unit = {
        bearerOperation(
            "prepareIdentityRecovery",
            ApiTags.IDENTITY,
            "Prepare a recovery",
            """
            Step one of replacing the server's key with the person's recovery key. The server creates a fresh
            signing key and builds the PLC operation that installs it, keeping the recovery key first in
            `rotationKeys`. It returns the operation, the exact bytes the recovery key must sign, and
            `preparedKey`: the fresh key and a digest of the operation, sealed with the server's
            key-encryption key. The server stores nothing until step two.

            Before signing, decode `unsignedOperationJson`, check that it is exactly the change asked for, and
            check that its canonical encoding is `signingPayloadBase64Url`. Then sign on the device and send
            the signature with `preparedKey` to **Complete a recovery** within 30 minutes. The recovery key
            never reaches the server. Requires the `atprotoRecoveryKeysV1` protocol feature; PLC publishing is
            not needed.
            """,
        )
        response {
            ok(
                "The operation to sign and the sealed key that completing it installs.",
                PrepareIdentityRecoveryResponse(
                    success = true,
                    data =
                        PrepareIdentityRecoveryData(
                            did = DocExamples.DID,
                            handle = DocExamples.HANDLE,
                            recoveryDidKey = RECOVERY_DID_KEY,
                            nextPublicKeyDidKey = NEXT_DID_KEY,
                            unsignedOperationJson =
                                """{"type":"plc_operation","rotationKeys":["$RECOVERY_DID_KEY","$NEXT_DID_KEY"],""" +
                                    """"prev":"bafyreih…"}""",
                            signingPayloadBase64Url = "omR0eXBlbXBsY19vcGVyYXRpb25scm90YXRpb25LZXlz…",
                            preparedKey = "q1W3…sealed-key…",
                        ),
                ),
            )
            apiError(HttpStatusCode.Conflict, recoveryKeysUnavailable, recoveryKeyNotSet, recoveryKeyNotOnIdentity)
            commonFailures()
        }
    }

    val completeIdentityRecovery: RouteConfig.() -> Unit = {
        bearerOperation(
            "completeIdentityRecovery",
            ApiTags.IDENTITY,
            "Complete a recovery",
            """
            Step two: send `preparedKey` from **Prepare a recovery**, the recovery key's signature over
            `signingPayloadBase64Url`, and `stepUp` from **Confirm a recovery-key change**. The server
            rebuilds the operation and refuses it as `IDENTITY_CHANGED` if anything about the identity changed
            since step one. It then checks that the signature is the registered recovery key's low-S signature,
            appends the operation, and makes the prepared key the server's key. The previous key loses all
            authority over the identity.

            Sending the same request again after a failure part-way through finishes the change instead of
            refusing it.
            """,
        )
        request {
            jsonBody(
                CompleteIdentityRecoveryRequest(
                    preparedKey = "q1W3…sealed-key…",
                    signature = "u7Xq…recovery-key-signature…",
                    stepUp = stepUpExample,
                ),
                "The sealed key from step one, the recovery key's signature (raw r||s with a low s, base64url) and the passkey " +
                    "confirmation.",
            )
        }
        response {
            ok(
                "The operation was appended and the new key is the server's key.",
                ImportSigningKeyResponse(
                    success = true,
                    data = ImportSigningKeyData(did = DocExamples.DID, handle = DocExamples.HANDLE, publicKeyDidKey = NEXT_DID_KEY),
                ),
            )
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase("PREPARED_KEY_REQUIRED", "`preparedKey` is blank.", "Prepared key is required"),
                ErrorCase("SIGNATURE_REQUIRED", "`signature` is blank.", "Recovery signature is required"),
                ErrorCase(
                    "PREPARED_KEY_INVALID",
                    "`preparedKey` was altered, sealed by another server, or belongs to another account.",
                    "The prepared key is not valid",
                ),
                ErrorCase(
                    "RECOVERY_SIGNATURE_INVALID",
                    "The signature is not the registered recovery key's low-S signature over `signingPayloadBase64Url`. " +
                        "Nothing was changed.",
                    "Client PLC recovery signature is invalid",
                ),
            )
            stepUpFailures()
            apiError(
                HttpStatusCode.Conflict,
                identityChanged,
                ErrorCase(
                    "PREPARED_KEY_EXPIRED",
                    "Step one was more than 30 minutes ago. Start again.",
                    "The prepared key has expired; start again",
                ),
                recoveryKeysUnavailable,
                stepUpUnavailable,
                recoveryKeyNotSet,
                recoveryKeyNotOnIdentity,
            )
            commonFailures()
        }
    }
}
