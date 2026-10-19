package app.logdate.server.identity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod
import studio.hypertext.atproto.plc.PlcDirectoryClient
import studio.hypertext.atproto.plc.PlcEncoding
import studio.hypertext.atproto.plc.PlcOperation
import studio.hypertext.atproto.plc.PlcOperations
import studio.hypertext.atproto.plc.PlcUnsignedOperation
import studio.hypertext.atproto.syntax.Handle
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Provisions hosted `did:plc` identities for LogDate accounts.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class PlcIdentityService(
    private val signingKeyService: SigningKeyService,
    private val config: AtprotoIdentityConfig,
    private val plcDirectoryClient: PlcDirectoryClient? = null,
    private val hostedPlcOperationRepository: HostedPlcOperationRepository? = null,
) {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = true
        }

    init {
        if (config.publishHostedPlcOperations) {
            require(plcDirectoryClient != null) { "PLC publishing requires a PLC directory client" }
        }
    }

    /**
     * Creates a signed PLC genesis operation and derives the hosted `did:plc`.
     */
    suspend fun provisionHostedDid(
        accountId: Uuid,
        handle: String,
        recoveryDidKey: String? = null,
    ): ProvisionedPlcIdentity {
        val activeKey = signingKeyService.ensureActiveKey(accountId)
        val publicKeyDidKey = "did:key:${activeKey.publicKeyMultibase}"
        val unsignedOperation =
            PlcOperations.atprotoGenesis(
                handle = handle,
                pdsServiceEndpoint = config.pdsServiceEndpoint,
                signingKeyDidKey = publicKeyDidKey,
                rotationKeys = listOfNotNull(recoveryDidKey, publicKeyDidKey).distinct(),
            )
        val signature =
            signOperation(
                payload = PlcEncoding.encodeUnsigned(unsignedOperation),
                privateKey = signingKeyService.decryptPrivateKey(activeKey),
                curve =
                    requireNotNull(EcCurve.fromSigningKeyAlgorithm(activeKey.algorithm)) {
                        "Unsupported signing key algorithm: ${activeKey.algorithm}"
                    },
            )
        val signedOperation = unsignedOperation.signed(signature)
        val did = PlcEncoding.deriveDid(signedOperation)

        if (config.publishHostedPlcOperations) {
            plcDirectoryClient
                ?.submit(did = did, entry = signedOperation)
                ?.getOrThrow()
        }
        persistHostedOperation(
            accountId = accountId,
            did = did,
            operation = signedOperation,
            resolvedCid = resolvePublishedCid(did, signedOperation),
        )

        return ProvisionedPlcIdentity(
            did = did.toString(),
            publicKeyMultibase = activeKey.publicKeyMultibase,
            publicKeyDidKey = publicKeyDidKey,
            operation = signedOperation,
        )
    }

    /**
     * Builds a DID document for [identity].
     */
    fun documentFor(identity: ProvisionedPlcIdentity): DidDocument =
        DidDocument(
            id = AtprotoDid.require(identity.did),
            alsoKnownAs = identity.operation.alsoKnownAs,
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = "${identity.did}#atproto",
                        type = "Multikey",
                        controller = AtprotoDid.require(identity.did),
                        publicKeyMultibase = identity.publicKeyMultibase,
                    ),
                ),
            service =
                listOf(
                    Service(
                        id = "#atproto_pds",
                        type = "AtprotoPersonalDataServer",
                        serviceEndpoint =
                            identity.operation.services
                                .getValue("atproto_pds")
                                .endpoint,
                    ),
                ),
        )

    /**
     * Rotates the active hosted PLC signing key and publishes a PLC update operation.
     */
    suspend fun rotateHostedDid(
        accountId: Uuid,
        did: AtprotoDid,
        handle: String,
        recoveryDidKey: String? = null,
        preparedKey: StoredSigningKey? = null,
    ): RotatedPlcIdentity {
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC rotation requires published PLC operations")
        }
        val plcClient = plcDirectoryClient ?: throw IdentityLifecycleConflictException("PLC publishing requires a PLC directory client")
        require(did.method == "plc") { "Hosted PLC rotation requires a did:plc identity" }

        val currentKey = signingKeyService.ensureActiveKey(accountId)
        val nextKey = preparedKey ?: signingKeyService.prepareKey(accountId)
        val latestIndexedOperation =
            plcClient
                .getAuditLog(did)
                .getOrThrow()
                .lastOrNull { !it.nullified }
                ?: throw IdentityLifecycleConflictException("No PLC audit log entry exists for $did")
        val nextPublicKeyDidKey = didKeyFor(nextKey.publicKeyMultibase)
        val unsignedOperation =
            buildHostedAtprotoUpdate(
                prevCid = latestIndexedOperation.cid,
                handle = handle,
                signingKeyDidKey = nextPublicKeyDidKey,
                recoveryDidKey = recoveryDidKey,
            )
        val signedOperation =
            unsignedOperation.signed(
                signOperation(
                    payload = PlcEncoding.encodeUnsigned(unsignedOperation),
                    privateKey = signingKeyService.decryptPrivateKey(currentKey),
                    curve =
                        requireNotNull(EcCurve.fromSigningKeyAlgorithm(currentKey.algorithm)) {
                            "Unsupported signing key algorithm: ${currentKey.algorithm}"
                        },
                ),
            )

        plcClient.submit(did, signedOperation).getOrThrow()
        val resolvedCid = resolvePublishedCid(did, signedOperation)
        persistHostedOperation(
            accountId = accountId,
            did = did,
            operation = signedOperation,
            resolvedCid = resolvedCid,
        )
        val activatedKey = signingKeyService.activatePreparedKey(accountId, nextKey)
        return RotatedPlcIdentity(
            did = did.toString(),
            previousPublicKeyMultibase = currentKey.publicKeyMultibase,
            publicKeyMultibase = activatedKey.publicKeyMultibase,
            publicKeyDidKey = nextPublicKeyDidKey,
            operation = signedOperation,
        )
    }

    suspend fun listStoredOperations(accountId: Uuid): List<StoredHostedPlcOperation> =
        hostedPlcOperationRepository?.listByAccountId(accountId).orEmpty()

    suspend fun listStoredOperations(did: AtprotoDid): List<StoredHostedPlcOperation> =
        hostedPlcOperationRepository?.listByDid(did.toString()).orEmpty()

    /**
     * Appends an update operation that lists the person's [recoveryDidKey] ahead of the server's key,
     * so it can overrule the server. The server signs it with its current key, which the previous
     * operation lists as a rotation key. The operation reaches the PLC directory only when publishing
     * is on; otherwise the hosted operation log is the identity's history.
     */
    suspend fun updateHostedRecoveryKey(
        accountId: Uuid,
        did: AtprotoDid,
        handle: String,
        recoveryDidKey: String,
    ): UpdatedPlcRecoveryKey {
        requireRecoveryKeys(did)

        val activeKey = signingKeyService.ensureActiveKey(accountId)
        val unsignedOperation =
            buildHostedAtprotoUpdate(
                prevCid = latestOperation(did).cid,
                handle = handle,
                signingKeyDidKey = didKeyFor(activeKey.publicKeyMultibase),
                recoveryDidKey = recoveryDidKey,
            )
        val signedOperation =
            unsignedOperation.signed(
                signOperation(
                    payload = PlcEncoding.encodeUnsigned(unsignedOperation),
                    privateKey = signingKeyService.decryptPrivateKey(activeKey),
                    curve =
                        requireNotNull(EcCurve.fromSigningKeyAlgorithm(activeKey.algorithm)) {
                            "Unsupported signing key algorithm: ${activeKey.algorithm}"
                        },
                ),
            )

        appendOperation(accountId = accountId, did = did, operation = signedOperation)
        return UpdatedPlcRecoveryKey(
            did = did.toString(),
            recoveryDidKey = recoveryDidKey,
            operation = signedOperation,
        )
    }

    /**
     * Builds the unsigned operation that installs [nextSigningKeyDidKey] as the server's key, for the
     * person's [recoveryDidKey] to sign. The recovery key stays first. The operation builds on the
     * identity's latest operation, which must list the recovery key as a rotation key.
     */
    suspend fun prepareHostedRecoveryImport(
        accountId: Uuid,
        did: AtprotoDid,
        handle: String,
        recoveryDidKey: String,
        nextSigningKeyDidKey: String,
    ): PreparedPlcRecoveryOperation {
        requireRecoveryKeys(did)

        val latest = latestOperation(did)
        if (recoveryDidKey !in latest.operation.rotationKeys) {
            throw IdentityLifecycleConflictException(
                "The identity's latest operation does not list the registered recovery key",
                code = RECOVERY_KEY_NOT_ON_IDENTITY,
            )
        }
        val unsignedOperation =
            buildHostedAtprotoUpdate(
                prevCid = latest.cid,
                handle = handle,
                signingKeyDidKey = nextSigningKeyDidKey,
                recoveryDidKey = recoveryDidKey,
            )

        return PreparedPlcRecoveryOperation(
            did = did.toString(),
            recoveryDidKey = recoveryDidKey,
            nextSigningKeyDidKey = nextSigningKeyDidKey,
            unsignedOperation = unsignedOperation,
            signingPayload = PlcEncoding.encodeUnsigned(unsignedOperation),
        )
    }

    /**
     * Attaches the client's recovery-key [signature] to [preparedOperation] and appends it, after
     * checking that the operation still builds on the identity's latest operation and that the
     * signature is the registered recovery key's.
     */
    suspend fun publishClientSignedRecoveryImport(
        accountId: Uuid,
        did: AtprotoDid,
        recoveryDidKey: String,
        preparedOperation: PreparedPlcRecoveryOperation,
        signature: String,
    ): PlcOperation {
        requireRecoveryKeys(did)
        require(preparedOperation.did == did.toString()) { "Prepared PLC recovery import belongs to a different DID" }
        require(preparedOperation.recoveryDidKey == recoveryDidKey) { "Prepared PLC recovery import belongs to a different recovery key" }
        if (preparedOperation.unsignedOperation.prev != latestOperation(did).cid) {
            throw IdentityLifecycleConflictException("The identity changed after this recovery was prepared", code = IDENTITY_CHANGED)
        }
        verifyRecoverySignature(
            recoveryDidKey = recoveryDidKey,
            payload = preparedOperation.signingPayload,
            signature = signature,
        )

        val signedOperation = preparedOperation.unsignedOperation.signed(signature)
        appendOperation(accountId = accountId, did = did, operation = signedOperation)
        return signedOperation
    }

    private fun requireRecoveryKeys(did: AtprotoDid) {
        if (!config.supportsRecoveryKeys) {
            throw IdentityLifecycleConflictException("Recovery keys are not enabled on this server", code = RECOVERY_KEYS_UNAVAILABLE)
        }
        if (did.method != "plc") {
            throw IdentityLifecycleConflictException("Recovery keys need a did:plc identity", code = RECOVERY_KEYS_UNAVAILABLE)
        }
    }

    /**
     * The identity's newest operation and its CID: from the PLC directory's audit log when this server
     * publishes, otherwise from the hosted operation log, which is then the identity's only history.
     */
    private suspend fun latestOperation(did: AtprotoDid): LatestPlcOperation {
        val plcClient = plcDirectoryClient
        if (config.publishHostedPlcOperations && plcClient != null) {
            val indexed =
                plcClient
                    .getAuditLog(did)
                    .getOrThrow()
                    .lastOrNull { !it.nullified }
                    ?: throw IdentityLifecycleConflictException("No PLC audit log entry exists for $did")
            val operation =
                indexed.operation as? PlcOperation
                    ?: throw IdentityLifecycleConflictException("The identity $did has been deactivated")
            return LatestPlcOperation(cid = indexed.cid, operation = operation)
        }
        val stored =
            listStoredOperations(did).lastOrNull()
                ?: throw IdentityLifecycleConflictException("No hosted PLC operation exists for $did")
        val operation = json.decodeFromString(PlcOperation.serializer(), stored.operationJson)
        return LatestPlcOperation(cid = stored.cid ?: PlcEncoding.operationCid(operation), operation = operation)
    }

    /** Submits [operation] to the PLC directory when this server publishes, then records it in the hosted log. */
    private suspend fun appendOperation(
        accountId: Uuid,
        did: AtprotoDid,
        operation: PlcOperation,
    ) {
        val plcClient = plcDirectoryClient
        if (config.publishHostedPlcOperations && plcClient != null) {
            plcClient.submit(did, operation).getOrThrow()
        }
        persistHostedOperation(
            accountId = accountId,
            did = did,
            operation = operation,
            resolvedCid = resolvePublishedCid(did, operation),
        )
    }

    /** The CID the directory reports for [operation] when this server publishes, otherwise the one computed locally. */
    private suspend fun resolvePublishedCid(
        did: AtprotoDid,
        operation: PlcOperation,
    ): String =
        plcDirectoryClient
            ?.takeIf { config.publishHostedPlcOperations }
            ?.getAuditLog(did)
            ?.getOrNull()
            ?.lastOrNull { indexedOperation ->
                !indexedOperation.nullified &&
                    indexedOperation.operation is PlcOperation &&
                    indexedOperation.operation.sig == operation.sig
            }?.cid
            ?: PlcEncoding.operationCid(operation)

    private suspend fun persistHostedOperation(
        accountId: Uuid,
        did: AtprotoDid,
        operation: PlcOperation,
        resolvedCid: String,
    ) {
        hostedPlcOperationRepository?.save(
            StoredHostedPlcOperation(
                id = Uuid.random(),
                accountId = accountId,
                did = did.toString(),
                cid = resolvedCid,
                prevCid = operation.prev,
                operationType = operation.type,
                operationJson = json.encodeToString(PlcOperation.serializer(), operation),
                createdAt = Clock.System.now(),
            ),
        )
    }

    private fun buildHostedAtprotoUpdate(
        prevCid: String,
        handle: String,
        signingKeyDidKey: String,
        recoveryDidKey: String?,
    ): PlcUnsignedOperation =
        PlcOperations.atprotoUpdate(
            prevCid = prevCid,
            handle = Handle.require(handle.trim().trim('.').lowercase()).toString(),
            pdsServiceEndpoint = config.pdsServiceEndpoint,
            signingKeyDidKey = signingKeyDidKey,
            rotationKeys = listOfNotNull(recoveryDidKey, signingKeyDidKey).distinct(),
        )

    private fun signOperation(
        payload: ByteArray,
        privateKey: java.security.PrivateKey,
        curve: EcCurve,
    ): String =
        Base64.UrlSafe
            .encode(EcKeySupport.signSha256(privateKey = privateKey, curve = curve, payload = payload))
            .trimEnd('=')

    private fun verifyRecoverySignature(
        recoveryDidKey: String,
        payload: ByteArray,
        signature: String,
    ) {
        val multikey = recoveryDidKey.removePrefix(DID_KEY_PREFIX)
        val decoded = EcKeySupport.decodePublicKey(multikey)
        val verified =
            runCatching {
                EcKeySupport.verifySha256(
                    publicKey = decoded.publicKey,
                    curve = decoded.curve,
                    payload = payload,
                    signature = decodeBase64Url(signature),
                )
            }.getOrDefault(false)
        if (!verified) {
            throw IdentityLifecycleValidationException("Client PLC recovery signature is invalid", code = RECOVERY_SIGNATURE_INVALID)
        }
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val normalized =
            when (value.length % 4) {
                0 -> value
                2 -> "$value=="
                3 -> "$value="
                else -> throw IllegalArgumentException("Invalid base64url value")
            }
        return Base64.UrlSafe.decode(normalized)
    }

    companion object {
        private const val DID_KEY_PREFIX: String = "did:key:"

        /** The server does not support recovery keys, or the identity is not a `did:plc`. */
        const val RECOVERY_KEYS_UNAVAILABLE: String = "RECOVERY_KEYS_UNAVAILABLE"

        /** The identity's latest operation does not list the registered recovery key. */
        const val RECOVERY_KEY_NOT_ON_IDENTITY: String = "RECOVERY_KEY_NOT_ON_IDENTITY"

        /** Another operation was appended between preparing and completing a recovery. */
        const val IDENTITY_CHANGED: String = "IDENTITY_CHANGED"

        /** The recovery signature is not the registered recovery key's signature over the operation. */
        const val RECOVERY_SIGNATURE_INVALID: String = "RECOVERY_SIGNATURE_INVALID"
    }
}

/** The newest operation in an identity's history and the CID the next operation names as `prev`. */
private data class LatestPlcOperation(
    val cid: String,
    val operation: PlcOperation,
)

/**
 * Signed hosted PLC identity material.
 */
data class ProvisionedPlcIdentity(
    val did: String,
    val publicKeyMultibase: String,
    val publicKeyDidKey: String,
    val operation: PlcOperation,
)

/**
 * Signed hosted PLC rotation material after a successful update operation.
 */
data class RotatedPlcIdentity(
    val did: String,
    val previousPublicKeyMultibase: String,
    val publicKeyMultibase: String,
    val publicKeyDidKey: String,
    val operation: PlcOperation,
)

/**
 * Hosted PLC recovery-key update result.
 */
data class UpdatedPlcRecoveryKey(
    val did: String,
    val recoveryDidKey: String,
    val operation: PlcOperation,
)

/**
 * Unsigned hosted PLC recovery import payload that a first-party client can sign locally.
 */
data class PreparedPlcRecoveryOperation(
    val did: String,
    val recoveryDidKey: String,
    val nextSigningKeyDidKey: String,
    val unsignedOperation: PlcUnsignedOperation,
    val signingPayload: ByteArray,
)
