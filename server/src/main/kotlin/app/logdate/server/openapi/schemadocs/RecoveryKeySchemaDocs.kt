package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

private const val SUCCESS_FLAG = "Always `true` on a successful response. Errors use the error envelope instead."
private const val STEP_UP_FIELD =
    "A passkey confirmation from **Confirm a recovery-key change**. Required; without it the change is refused " +
        "with `STEP_UP_REQUIRED`."

/** Prose for the recovery-key schemas: the person's own key for their identity and the passkey step-up it needs. */
internal object RecoveryKeySchemaDocs {
    val docs: Map<String, SchemaDoc> =
        mapOf(
            "PrepareIdentityRecoveryResponse" to
                SchemaDoc(
                    "The recovery to sign.",
                    mapOf(
                        "success" to SUCCESS_FLAG,
                        "data" to "What to sign, and the sealed key it installs.",
                    ),
                ),
            "PrepareIdentityRecoveryData" to
                SchemaDoc(
                    "An unsigned PLC operation that replaces the server's key, the bytes the recovery key must sign, and the sealed new key.",
                    mapOf(
                        "did" to "The identity's DID.",
                        "handle" to "The identity's handle.",
                        "recoveryDidKey" to "The registered recovery key that must produce the signature.",
                        "nextPublicKeyDidKey" to "The server's new public key, which the operation installs.",
                        "unsignedOperationJson" to "The PLC operation as JSON, for inspection.",
                        "signingPayloadBase64Url" to "The exact bytes to sign with the recovery key, base64url.",
                        "preparedKey" to
                            "The new key sealed by the server. Send it back unchanged to complete the recovery within 30 minutes.",
                    ),
                ),
            "CompleteIdentityRecoveryRequest" to
                SchemaDoc(
                    "The signed recovery.",
                    mapOf(
                        "preparedKey" to "`preparedKey` from the prepare step, unchanged.",
                        "signature" to "The recovery key's signature over `signingPayloadBase64Url`: raw r||s with a low s, base64url.",
                        "stepUp" to STEP_UP_FIELD,
                    ),
                ),
            "RegisterPlcRecoveryKeyRequest" to
                SchemaDoc(
                    "A recovery key to register, or to put in place of the registered one.",
                    mapOf("recoveryDidKey" to "The public recovery key as a `did:key`.", "stepUp" to STEP_UP_FIELD),
                ),
            "RecoveryKeyStepUpResponse" to
                SchemaDoc(
                    "A challenge for one of the account's passkeys, to confirm a recovery-key change.",
                    mapOf("success" to SUCCESS_FLAG, "data" to "The WebAuthn request options."),
                ),
            "PasskeyStepUpProof" to
                SchemaDoc(
                    "A fresh passkey confirmation for a change a session alone cannot make.",
                    mapOf(
                        "challenge" to "The challenge from **Confirm a recovery-key change**, unchanged.",
                        "credential" to "The passkey assertion over that challenge, as the platform returned it.",
                    ),
                ),
            "RegisterPlcRecoveryKeyResponse" to
                SchemaDoc("The registered recovery key.", mapOf("success" to SUCCESS_FLAG, "data" to "The identity and its recovery key.")),
            "RegisterPlcRecoveryKeyData" to
                SchemaDoc(
                    "An identity and its recovery key.",
                    mapOf(
                        "did" to "The identity's DID.",
                        "handle" to "The identity's handle.",
                        "recoveryDidKey" to "The registered recovery key.",
                    ),
                ),
        )
}
