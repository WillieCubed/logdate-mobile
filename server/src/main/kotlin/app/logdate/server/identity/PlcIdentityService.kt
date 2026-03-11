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
import studio.hypertext.atproto.plc.PlcService
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
        val normalizedHandle = Handle.require(handle.trim().trim('.').lowercase()).toString()
        val nextPublicKeyDidKey = didKeyFor(nextKey.publicKeyMultibase)
        val unsignedOperation =
            PlcUnsignedOperation(
                prev = latestIndexedOperation.cid,
                services =
                    mapOf(
                        "atproto_pds" to
                            PlcService(
                                type = "AtprotoPersonalDataServer",
                                endpoint = config.pdsServiceEndpoint,
                            ),
                    ),
                alsoKnownAs = listOf("at://$normalizedHandle"),
                rotationKeys = listOfNotNull(nextPublicKeyDidKey, recoveryDidKey).distinct(),
                verificationMethods = mapOf("atproto" to nextPublicKeyDidKey),
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
        val normalizedHandle = Handle.require(handle.trim().trim('.').lowercase()).toString()
        val unsignedOperation =
            PlcUnsignedOperation(
                prev = latestIndexedOperation.cid,
                services =
                    mapOf(
                        "atproto_pds" to
                            PlcService(
                                type = "AtprotoPersonalDataServer",
                                endpoint = config.pdsServiceEndpoint,
                            ),
                    ),
                alsoKnownAs = listOf("at://$normalizedHandle"),
                rotationKeys = listOf(activePublicKeyDidKey, recoveryDidKey).distinct(),
                verificationMethods = mapOf("atproto" to activePublicKeyDidKey),
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

    private fun signOperation(
        payload: ByteArray,
        privateKey: java.security.PrivateKey,
        curve: EcCurve,
    ): String =
        Base64.UrlSafe
            .encode(EcKeySupport.signSha256(privateKey = privateKey, curve = curve, payload = payload))
            .trimEnd('=')

    companion object {
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
