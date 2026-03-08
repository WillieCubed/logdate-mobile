package app.logdate.server.identity

import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod
import studio.hypertext.atproto.plc.PlcDirectoryClient
import studio.hypertext.atproto.plc.PlcEncoding
import studio.hypertext.atproto.plc.PlcOperation
import studio.hypertext.atproto.plc.PlcOperations
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
) {
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
    ): ProvisionedPlcIdentity {
        val activeKey = signingKeyService.ensureActiveKey(accountId)
        val publicKeyDidKey = "did:key:${activeKey.publicKeyMultibase}"
        val unsignedOperation =
            PlcOperations.atprotoGenesis(
                handle = handle,
                pdsServiceEndpoint = config.pdsServiceEndpoint,
                signingKeyDidKey = publicKeyDidKey,
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

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
