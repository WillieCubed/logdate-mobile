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
                rotationKeys = listOfNotNull(publicKeyDidKey, recoveryDidKey).distinct(),
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
     * Publishes a hosted PLC update operation that adds or replaces the user-controlled recovery key.
     */
    suspend fun updateHostedRecoveryKey(
        accountId: Uuid,
        did: AtprotoDid,
        handle: String,
        recoveryDidKey: String,
    ): UpdatedPlcRecoveryKey {
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC recovery keys require published PLC operations")
        }
        val plcClient = plcDirectoryClient ?: throw IdentityLifecycleConflictException("PLC publishing requires a PLC directory client")
        require(did.method == "plc") { "Hosted PLC recovery keys require a did:plc identity" }

        val activeKey = signingKeyService.ensureActiveKey(accountId)
        val activePublicKeyDidKey = didKeyFor(activeKey.publicKeyMultibase)
        val latestIndexedOperation =
            plcClient
                .getAuditLog(did)
                .getOrThrow()
                .lastOrNull { !it.nullified }
                ?: throw IdentityLifecycleConflictException("No PLC audit log entry exists for $did")
        val unsignedOperation =
            buildHostedAtprotoUpdate(
                prevCid = latestIndexedOperation.cid,
                handle = handle,
                signingKeyDidKey = activePublicKeyDidKey,
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

        plcClient.submit(did, signedOperation).getOrThrow()
        val resolvedCid = resolvePublishedCid(did, signedOperation)
        persistHostedOperation(
            accountId = accountId,
            did = did,
            operation = signedOperation,
            resolvedCid = resolvedCid,
        )
        return UpdatedPlcRecoveryKey(
            did = did.toString(),
            recoveryDidKey = recoveryDidKey,
            operation = signedOperation,
        )
    }

    suspend fun prepareHostedRecoveryImport(
        accountId: Uuid,
        did: AtprotoDid,
        handle: String,
        recoveryDidKey: String,
        nextSigningKeyDidKey: String,
    ): PreparedPlcRecoveryOperation {
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC recovery import requires published PLC operations")
        }
        val plcClient = plcDirectoryClient ?: throw IdentityLifecycleConflictException("PLC publishing requires a PLC directory client")
        require(did.method == "plc") { "Hosted PLC recovery import requires a did:plc identity" }

        val latestIndexedOperation =
            plcClient
                .getAuditLog(did)
                .getOrThrow()
                .lastOrNull { !it.nullified }
                ?: throw IdentityLifecycleConflictException("No PLC audit log entry exists for $did")
        val unsignedOperation =
            buildHostedAtprotoUpdate(
                prevCid = latestIndexedOperation.cid,
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

    suspend fun publishClientSignedRecoveryImport(
        accountId: Uuid,
        did: AtprotoDid,
        recoveryDidKey: String,
        preparedOperation: PreparedPlcRecoveryOperation,
        signature: String,
    ): PlcOperation {
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC recovery import requires published PLC operations")
        }
        val plcClient = plcDirectoryClient ?: throw IdentityLifecycleConflictException("PLC publishing requires a PLC directory client")
        require(did.method == "plc") { "Hosted PLC recovery import requires a did:plc identity" }
        require(preparedOperation.did == did.toString()) { "Prepared PLC recovery import belongs to a different DID" }
        require(preparedOperation.recoveryDidKey == recoveryDidKey) { "Prepared PLC recovery import belongs to a different recovery key" }
        verifyRecoverySignature(
            recoveryDidKey = recoveryDidKey,
            payload = preparedOperation.signingPayload,
            signature = signature,
        )

        val signedOperation = preparedOperation.unsignedOperation.signed(signature)
        plcClient.submit(did, signedOperation).getOrThrow()
        val resolvedCid = resolvePublishedCid(did, signedOperation)
        persistHostedOperation(
            accountId = accountId,
            did = did,
            operation = signedOperation,
            resolvedCid = resolvedCid,
        )
        return signedOperation
    }

    private suspend fun resolvePublishedCid(
        did: AtprotoDid,
        operation: PlcOperation,
    ): String? =
        plcDirectoryClient
            ?.getAuditLog(did)
            ?.getOrNull()
            ?.lastOrNull { indexedOperation ->
                !indexedOperation.nullified &&
                    indexedOperation.operation is PlcOperation &&
                    indexedOperation.operation.sig == operation.sig
            }?.cid

    private suspend fun persistHostedOperation(
        accountId: Uuid,
        did: AtprotoDid,
        operation: PlcOperation,
        resolvedCid: String?,
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
            rotationKeys = listOfNotNull(signingKeyDidKey, recoveryDidKey).distinct(),
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
        require(
            EcKeySupport.verifySha256(
                publicKey = decoded.publicKey,
                curve = decoded.curve,
                payload = payload,
                signature = decodeBase64Url(signature),
            ),
        ) { "Client PLC recovery signature is invalid" }
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

    private companion object {
        private const val DID_KEY_PREFIX: String = "did:key:"
    }
}

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
