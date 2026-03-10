package app.logdate.server.identity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
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
    ): RotatedPlcIdentity {
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC rotation requires published PLC operations")
        }
        val plcClient = plcDirectoryClient ?: throw IdentityLifecycleConflictException("PLC publishing requires a PLC directory client")
        require(did.method == "plc") { "Hosted PLC rotation requires a did:plc identity" }

        val currentKey = signingKeyService.ensureActiveKey(accountId)
        val nextKey = signingKeyService.prepareKey(accountId)
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
        privateKey: PrivateKey,
    ): String {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey, deterministicRandom(privateKey = privateKey, payload = payload))
        signature.update(payload)
        val rawSignature = signature.sign()
        val ecPrivateKey = privateKey as ECPrivateKey
        val curveOrder = ecPrivateKey.params.order
        val halfOrder = curveOrder.shiftRight(1)
        val r = BigInteger(1, rawSignature.copyOfRange(0, P256_COORDINATE_BYTES))
        val s = BigInteger(1, rawSignature.copyOfRange(P256_COORDINATE_BYTES, rawSignature.size))
        val normalizedS = if (s > halfOrder) curveOrder.subtract(s) else s
        val normalizedSignature = r.toFixedWidth(P256_COORDINATE_BYTES) + normalizedS.toFixedWidth(P256_COORDINATE_BYTES)
        return Base64.UrlSafe.encode(normalizedSignature).trimEnd('=')
    }

    private fun deterministicRandom(
        privateKey: PrivateKey,
        payload: ByteArray,
    ): SecureRandom =
        SecureRandom.getInstance(DETERMINISTIC_PRNG_ALGORITHM).apply {
            val seed = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(privateKey.encoded + payload)
            setSeed(seed)
        }

    companion object {
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format"
        private const val DETERMINISTIC_PRNG_ALGORITHM = "SHA1PRNG"
        private const val SHA_256_ALGORITHM = "SHA-256"
        private const val P256_COORDINATE_BYTES = 32
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

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
