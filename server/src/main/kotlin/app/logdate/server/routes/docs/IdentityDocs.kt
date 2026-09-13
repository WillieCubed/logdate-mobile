package app.logdate.server.routes.docs

import app.logdate.server.identity.SigningKeyService
import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.CompleteRecoverySigningKeyImportRequest
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.ExportSigningKeyData
import app.logdate.server.routes.ExportSigningKeyRequest
import app.logdate.server.routes.ExportSigningKeyResponse
import app.logdate.server.routes.HostedPlcOperationData
import app.logdate.server.routes.HostedPlcOperationsResponse
import app.logdate.server.routes.IdentityStatusData
import app.logdate.server.routes.IdentityStatusResponse
import app.logdate.server.routes.ImportSigningKeyData
import app.logdate.server.routes.ImportSigningKeyRequest
import app.logdate.server.routes.ImportSigningKeyResponse
import app.logdate.server.routes.PrepareRecoverySigningKeyImportData
import app.logdate.server.routes.PrepareRecoverySigningKeyImportRequest
import app.logdate.server.routes.PrepareRecoverySigningKeyImportResponse
import app.logdate.server.routes.RegisterPlcRecoveryKeyData
import app.logdate.server.routes.RegisterPlcRecoveryKeyRequest
import app.logdate.server.routes.RegisterPlcRecoveryKeyResponse
import app.logdate.server.routes.RotateSigningKeyData
import app.logdate.server.routes.RotateSigningKeyRequest
import app.logdate.server.routes.RotateSigningKeyResponse
import app.logdate.server.routes.apiError
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.publicOperation
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod

/** Documentation for `IdentityApiRoutes.kt` and the `.well-known` DID routes in `IdentityRoutes.kt`. */
internal object IdentityDocs {
    val didDocumentExample: DidDocument =
        DidDocument(
            id = AtprotoDid.require("did:web:${DocExamples.HANDLE}"),
            alsoKnownAs = listOf("at://${DocExamples.HANDLE}"),
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = "did:web:${DocExamples.HANDLE}#atproto",
                        type = "Multikey",
                        controller = AtprotoDid.require("did:web:${DocExamples.HANDLE}"),
                        publicKeyMultibase = PUBLIC_MULTIBASE,
                    ),
                ),
            service =
                listOf(
                    Service(
                        id = "#atproto_pds",
                        type = "AtprotoPersonalDataServer",
                        serviceEndpoint = "https://cloud.logdate.app",
                    ),
                ),
        )

    private const val PUBLIC_MULTIBASE = "zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme"
    private const val PUBLIC_DID_KEY = "did:key:$PUBLIC_MULTIBASE"
    private const val NEXT_DID_KEY = "did:key:zQ3shXjHeiBuRCKmM36cuYnm7YEMzhGnCmCyW92sRJ9pribSF"
    private const val RECOVERY_DID_KEY = "did:key:zQ3shbgnTtF5t6yKw3KwDmgb4iFdGb3dUDbKsGxTMVaMdumoT"
    private const val PASSPHRASE = "correct horse battery staple"

    private val exportedKey =
        SigningKeyService.ExportedSigningKey(
            algorithm = "secp256k1",
            publicKeyMultibase = PUBLIC_MULTIBASE,
            publicKeyDidKey = PUBLIC_DID_KEY,
            encryptedPrivateKey = "q8m2Zk1vT3y5Wb9x…",
            salt = "c2FsdC1zYWx0LXNhbHQ",
            iv = "aXYtaXYtaXYtaXY",
        )

    private val passphraseRequired =
        ErrorCase(
            "PASSPHRASE_REQUIRED",
            "`passphrase` is blank. It protects the private key; choose a strong one.",
            "Passphrase is required",
        )

    private fun ResponsesConfig.identityFailures(
        invalidCode: String,
        invalidMeaning: String,
        conflictCode: String,
        conflictMeaning: String,
        failedCode: String,
        failedMessage: String,
        vararg extraBadRequest: ErrorCase,
    ) {
        apiError(HttpStatusCode.BadRequest, *extraBadRequest, ErrorCase(invalidCode, invalidMeaning, "Invalid request"))
        bearerUnauthorized(ErrorEnvelope.API)
        apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
        apiError(HttpStatusCode.Conflict, ErrorCase(conflictCode, conflictMeaning, "Identity state conflict"))
        apiError(
            HttpStatusCode.InternalServerError,
            ErrorCase(
                failedCode,
                "Something failed on the server, or the body was not valid JSON for this request (that case answers `500` here, " +
                    "not `400`). Check the body first; retry with backoff only if it is well-formed.",
                failedMessage,
            ),
        )
    }

    val getIdentityStatus: RouteConfig.() -> Unit = {
        bearerOperation(
            "getIdentityStatus",
            ApiTags.IDENTITY,
            "Get the identity",
            """
            Returns the account's AT Protocol identity: its DID, its handle, the public half of the signing key
            (as multibase and as a `did:key`), the registered PLC recovery key if any, and how many PLC
            operations the server has published for it.

            A DID (decentralized identifier) is the stable ID other servers use for this person; the handle is
            the human-readable name that resolves to it. The server creates both when the account is created,
            so this call never needs a setup step.
            """,
        )
        response {
            ok(
                "The identity and its keys.",
                IdentityStatusResponse(
                    success = true,
                    data =
                        IdentityStatusData(
                            did = DocExamples.DID,
                            handle = DocExamples.HANDLE,
                            signingKeyPublicMultibase = PUBLIC_MULTIBASE,
                            signingKeyDidKey = PUBLIC_DID_KEY,
                            plcRecoveryDidKey = RECOVERY_DID_KEY,
                            plcOperationCount = 2,
                        ),
                ),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "IDENTITY_STATUS_CONFLICT",
                    "The stored identity is in an inconsistent state (for example the DID and the published key disagree). Contact support.",
                    "Identity state conflict",
                ),
            )
            apiError(
                HttpStatusCode.InternalServerError,
                ErrorCase(
                    "IDENTITY_STATUS_FAILED",
                    "Something failed on the server. Retry with backoff.",
                    "Failed to load identity status",
                ),
            )
        }
    }

    val exportSigningKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "exportSigningKey",
            ApiTags.IDENTITY,
            "Export the signing key",
            """
            Hands the person a copy of their identity's private signing key, encrypted with a passphrase they
            choose. This is what lets them move the identity to another server later, or keep a backup that
            does not depend on LogDate.

            The key is encrypted with AES-GCM under a PBKDF2-derived key before it leaves the server; the
            passphrase is never stored. Keep the exported blob and the passphrase apart.
            """,
        )
        request { jsonBody(ExportSigningKeyRequest(passphrase = PASSPHRASE), "The passphrase to encrypt the export with.") }
        response {
            ok(
                "The encrypted export. Store it somewhere safe.",
                ExportSigningKeyResponse(
                    success = true,
                    data = ExportSigningKeyData(did = DocExamples.DID, handle = DocExamples.HANDLE, exportedKey = exportedKey),
                ),
            )
            identityFailures(
                "SIGNING_KEY_EXPORT_INVALID",
                "Reserved for payload validation failures; not sent today.",
                "SIGNING_KEY_EXPORT_CONFLICT",
                "The identity is mid-change (a rotation or import is in flight). Wait a moment and retry.",
                "SIGNING_KEY_EXPORT_FAILED",
                "Failed to export signing key",
                passphraseRequired,
            )
        }
    }

    val rotateSigningKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "rotateSigningKey",
            ApiTags.IDENTITY,
            "Rotate the signing key",
            """
            Replaces the identity's signing key with a new one and publishes the change, then returns the new
            key encrypted under the passphrase you supply, exactly like **Export the signing key**. Rotate when
            an export may have leaked, or as routine hygiene.

            The previous public key is returned so you can confirm what was replaced. On deployments that
            publish to the PLC directory, the rotation is recorded as a PLC operation.
            """,
        )
        request { jsonBody(RotateSigningKeyRequest(passphrase = PASSPHRASE), "The passphrase to encrypt the new key's export with.") }
        response {
            ok(
                "The key was rotated. The response carries the new key's encrypted export.",
                RotateSigningKeyResponse(
                    success = true,
                    data =
                        RotateSigningKeyData(
                            did = DocExamples.DID,
                            handle = DocExamples.HANDLE,
                            previousPublicKeyDidKey = PUBLIC_DID_KEY,
                            exportedKey =
                                exportedKey.copy(
                                    publicKeyMultibase = NEXT_DID_KEY.removePrefix("did:key:"),
                                    publicKeyDidKey = NEXT_DID_KEY,
                                ),
                        ),
                ),
            )
            identityFailures(
                "SIGNING_KEY_ROTATION_INVALID",
                "Reserved for payload validation failures; not sent today.",
                "SIGNING_KEY_ROTATION_CONFLICT",
                "The identity cannot be rotated right now without risking an inconsistent published state. Wait and retry.",
                "SIGNING_KEY_ROTATION_FAILED",
                "Failed to rotate signing key",
                passphraseRequired,
            )
        }
    }

    val importSigningKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "importSigningKey",
            ApiTags.IDENTITY,
            "Import a signing key",
            """
            Installs a previously exported signing key as the identity's current key. Use it when the person
            brings an identity with them: the export from **Export the signing key** plus its passphrase.

            The server decrypts the blob, checks the key matches what the identity's published document
            expects, and switches to it. If the published document names a different key, use the recovery
            import flow instead: **Prepare a recovery import** and **Complete a recovery import**.
            """,
        )
        request {
            jsonBody(
                ImportSigningKeyRequest(passphrase = PASSPHRASE, exportedKey = exportedKey),
                "The export and the passphrase it was encrypted with.",
            )
        }
        response {
            ok(
                "The key is installed and in use.",
                ImportSigningKeyResponse(
                    success = true,
                    data = ImportSigningKeyData(did = DocExamples.DID, handle = DocExamples.HANDLE, publicKeyDidKey = PUBLIC_DID_KEY),
                ),
            )
            identityFailures(
                "SIGNING_KEY_IMPORT_INVALID",
                "The export could not be decrypted with this passphrase, or its contents are malformed.",
                "SIGNING_KEY_IMPORT_CONFLICT",
                "The imported key does not match the identity's published state. Use the recovery import flow.",
                "SIGNING_KEY_IMPORT_FAILED",
                "Failed to import signing key",
                passphraseRequired,
            )
        }
    }

    val prepareRecoverySigningKeyImport: RouteConfig.() -> Unit = {
        bearerOperation(
            "prepareRecoverySigningKeyImport",
            ApiTags.IDENTITY,
            "Prepare a recovery import",
            """
            Step one of importing a key when the identity's published PLC state no longer matches what the
            server holds, which needs the PLC *recovery key* to authorize the change. The server builds the
            unsigned PLC operation that would install the imported key and returns it along with the exact
            bytes to sign.

            Sign `signingPayloadBase64Url` with the recovery key on the device, then send the signature to
            **Complete a recovery import**. The recovery key never touches the server.
            """,
        )
        request {
            jsonBody(
                PrepareRecoverySigningKeyImportRequest(passphrase = PASSPHRASE, exportedKey = exportedKey),
                "The export to install and the passphrase it was encrypted with.",
            )
        }
        response {
            ok(
                "The operation to sign. `signingPayloadBase64Url` is what the recovery key must sign.",
                PrepareRecoverySigningKeyImportResponse(
                    success = true,
                    data =
                        PrepareRecoverySigningKeyImportData(
                            did = DocExamples.DID,
                            handle = DocExamples.HANDLE,
                            recoveryDidKey = RECOVERY_DID_KEY,
                            nextPublicKeyDidKey = PUBLIC_DID_KEY,
                            unsignedOperationJson =
                                """{"type":"plc_operation","rotationKeys":["$RECOVERY_DID_KEY","$PUBLIC_DID_KEY"],""" +
                                    """"prev":"bafyreih…"}""",
                            signingPayloadBase64Url = "omR0eXBlbXBsY19vcGVyYXRpb25scm90YXRpb25LZXlz…",
                        ),
                ),
            )
            identityFailures(
                "SIGNING_KEY_RECOVERY_PREPARE_INVALID",
                "The export could not be decrypted with this passphrase, or its contents are malformed.",
                "SIGNING_KEY_RECOVERY_PREPARE_CONFLICT",
                "No recovery key is registered for this identity, or the published state does not allow recovery. Register one first.",
                "SIGNING_KEY_RECOVERY_PREPARE_FAILED",
                "Failed to prepare signing key recovery import",
                passphraseRequired,
            )
        }
    }

    val completeRecoverySigningKeyImport: RouteConfig.() -> Unit = {
        bearerOperation(
            "completeRecoverySigningKeyImport",
            ApiTags.IDENTITY,
            "Complete a recovery import",
            """
            Step two: send the same export and passphrase as **Prepare a recovery import** plus the signature
            the recovery key produced over `signingPayloadBase64Url`. The server publishes the signed PLC
            operation and installs the key.
            """,
        )
        request {
            jsonBody(
                CompleteRecoverySigningKeyImportRequest(
                    passphrase = PASSPHRASE,
                    exportedKey = exportedKey,
                    signature = "MEQCIFm3x…recovery-key-signature…",
                ),
                "The export, its passphrase, and the recovery key's signature (base64url).",
            )
        }
        response {
            ok(
                "The operation was published and the key is installed.",
                ImportSigningKeyResponse(
                    success = true,
                    data = ImportSigningKeyData(did = DocExamples.DID, handle = DocExamples.HANDLE, publicKeyDidKey = PUBLIC_DID_KEY),
                ),
            )
            identityFailures(
                "SIGNING_KEY_RECOVERY_INVALID",
                "The signature does not verify with the registered recovery key, or the export is malformed. Redo step one.",
                "SIGNING_KEY_RECOVERY_CONFLICT",
                "The published state changed since step one. Start again from **Prepare a recovery import**.",
                "SIGNING_KEY_RECOVERY_FAILED",
                "Failed to complete signing key recovery import",
                passphraseRequired,
                ErrorCase("SIGNATURE_REQUIRED", "`signature` is blank.", "Recovery signature is required"),
            )
        }
    }

    val registerPlcRecoveryKey: RouteConfig.() -> Unit = {
        bearerOperation(
            "registerPlcRecoveryKey",
            ApiTags.IDENTITY,
            "Register a recovery key",
            """
            Records a `did:key` the person controls as the identity's PLC recovery key and publishes it. With a
            recovery key registered, the person can reclaim the identity even if the server's signing key is
            lost, using the recovery import flow. Generate the key pair on the device and keep the private
            half offline.
            """,
        )
        request { jsonBody(RegisterPlcRecoveryKeyRequest(recoveryDidKey = RECOVERY_DID_KEY), "The public recovery key as a `did:key`.") }
        response {
            ok(
                "The recovery key is registered and published.",
                RegisterPlcRecoveryKeyResponse(
                    success = true,
                    data =
                        RegisterPlcRecoveryKeyData(
                            did = DocExamples.DID,
                            handle = DocExamples.HANDLE,
                            recoveryDidKey = RECOVERY_DID_KEY,
                        ),
                ),
            )
            identityFailures(
                "PLC_RECOVERY_KEY_INVALID",
                "`recoveryDidKey` is not a valid `did:key` for a supported curve.",
                "PLC_RECOVERY_KEY_CONFLICT",
                "This deployment does not publish to PLC, or the identity is not a `did:plc`. Nothing was changed.",
                "PLC_RECOVERY_KEY_FAILED",
                "Failed to register PLC recovery key",
                ErrorCase("RECOVERY_DID_KEY_REQUIRED", "`recoveryDidKey` is blank.", "Recovery DID key is required"),
            )
        }
    }

    val listPlcOperations: RouteConfig.() -> Unit = {
        bearerOperation(
            "listPlcOperations",
            ApiTags.IDENTITY,
            "List PLC operations",
            """
            Returns every PLC operation this server has published for the identity, oldest first: the creation
            operation, key rotations and recovery-key registrations. Each entry carries the operation's content
            identifier (`cid`), the one it builds on (`prevCid`) and the raw operation JSON, which is enough to
            audit the identity's history against the public PLC directory.
            """,
        )
        response {
            ok(
                "The published operations, oldest first.",
                HostedPlcOperationsResponse(
                    success = true,
                    data =
                        listOf(
                            HostedPlcOperationData(
                                did = DocExamples.DID,
                                cid = "bafyreiha2g4xx3ny4ftcxg5dydjklm6q2nw7rz4bqdxu6yi3k7ovz5m7ei",
                                prevCid = null,
                                operationType = "plc_operation",
                                operationJson = """{"type":"plc_operation","rotationKeys":["$RECOVERY_DID_KEY"],"prev":null}""",
                                createdAt = DocExamples.ISO_NOW,
                            ),
                        ),
                ),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "PLC_OPERATION_HISTORY_CONFLICT",
                    "The identity is not a `did:plc`, so it has no PLC history.",
                    "Identity has no PLC operation history",
                ),
            )
            apiError(
                HttpStatusCode.InternalServerError,
                ErrorCase(
                    "PLC_OPERATION_HISTORY_FAILED",
                    "Something failed on the server. Retry with backoff.",
                    "Failed to load PLC operation history",
                ),
            )
        }
    }

    val resolveAtprotoDid: RouteConfig.() -> Unit = {
        publicOperation(
            "resolveAtprotoDid",
            ApiTags.IDENTITY,
            "Resolve a handle to a DID",
            """
            The AT Protocol's HTTP handle-resolution well-known endpoint. Other servers fetch
            `https://<handle>/.well-known/atproto-did` to learn which DID a handle belongs to; the answer is the
            DID as plain text.

            The handle is taken from the request's `Host` header, so this only works when the handle's DNS
            points at this server. There are no parameters.
            """,
        )
        response {
            code(HttpStatusCode.OK) {
                description = "The DID for the handle in the `Host` header, as `text/plain`."
                body<String> {
                    mediaTypes(ContentType.Text.Plain)
                    example("Example") { value = DocExamples.DID }
                }
            }
            code(HttpStatusCode.NotFound) { description = "No account has the handle named by the `Host` header. The body is empty." }
        }
    }

    val getDidDocument: RouteConfig.() -> Unit = {
        publicOperation(
            "getDidDocument",
            ApiTags.IDENTITY,
            "Get a DID document",
            """
            Serves the W3C DID document for a `did:web` identity hosted here, or for the server itself when the
            `Host` header is the server's own handle domain. A DID document lists the identity's public keys
            and the services (like this server) that act for it; AT Protocol clients read it to verify
            signatures and find the person's data server.

            Identities that are `did:plc` rather than `did:web` are published in the PLC directory instead and
            answer `404` here.
            """,
        )
        response {
            ok("The DID document for the identity named by the `Host` header.", didDocumentExample)
            code(HttpStatusCode.NotFound) {
                description = "No hosted `did:web` identity has the handle named by the `Host` header. The body is empty."
            }
        }
    }
}
