package studio.hypertext.atproto.plc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.identity.AtprotoDid

/**
 * PLC directory log entry payload.
 *
 * Implementations map to the wire-format `type` discriminator used by the PLC
 * directory.
 *
 * @property sig Signature attached to the entry, or `null` for unsigned helpers.
 * @property prev CID of the previous operation when present.
 * @property type PLC entry discriminator.
 */
@Serializable(with = PlcLogEntrySerializer::class)
public sealed interface PlcLogEntry {
    public val sig: String?
    public val prev: String?
    public val type: String

    /**
     * Returns `true` when this entry includes a non-blank signature.
     */
    public val isSigned: Boolean
        get() = !sig.isNullOrBlank()
}

/**
 * Canonical PLC operation payload used for genesis and update submissions.
 *
 * @property sig Signature for the operation, or `null` before signing.
 * @property prev CID of the previous PLC entry, or `null` for genesis operations.
 * @property type PLC directory discriminator. Defaults to [TYPE].
 * @property services Named PLC service definitions.
 * @property alsoKnownAs Alternate URIs associated with the identity.
 * @property rotationKeys Rotation keys permitted to sign future operations.
 * @property verificationMethods Verification methods published by the operation.
 */
@Serializable
public data class PlcOperation(
    override val sig: String? = null,
    override val prev: String? = null,
    override val type: String = TYPE,
    val services: Map<String, PlcService>,
    val alsoKnownAs: List<String>,
    val rotationKeys: List<String>,
    val verificationMethods: Map<String, String>,
) : PlcLogEntry {
    /**
     * Returns the unsigned PLC operation payload, excluding [sig].
     */
    public fun unsigned(): PlcUnsignedOperation =
        PlcUnsignedOperation(
            prev = prev,
            type = type,
            services = services,
            alsoKnownAs = alsoKnownAs,
            rotationKeys = rotationKeys,
            verificationMethods = verificationMethods,
        )

    /**
     * Returns `true` when this operation is a PLC genesis op.
     */
    public val isGenesis: Boolean
        get() = prev == null

    /**
     * Returns a copy of this operation with [signature].
     */
    public fun withSignature(signature: String): PlcOperation = copy(sig = signature)

    public companion object {
        /**
         * PLC directory discriminator for normal operations.
         */
        public const val TYPE: String = "plc_operation"
    }
}

/**
 * Unsigned PLC operation payload used for deterministic encoding and signing.
 *
 * @property prev CID of the previous PLC entry, or `null` for genesis operations.
 * @property type PLC directory discriminator. Defaults to [PlcOperation.TYPE].
 * @property services Named PLC service definitions.
 * @property alsoKnownAs Alternate URIs associated with the identity.
 * @property rotationKeys Rotation keys permitted to sign future operations.
 * @property verificationMethods Verification methods published by the operation.
 */
@Serializable
public data class PlcUnsignedOperation(
    val prev: String? = null,
    val type: String = PlcOperation.TYPE,
    val services: Map<String, PlcService>,
    val alsoKnownAs: List<String>,
    val rotationKeys: List<String>,
    val verificationMethods: Map<String, String>,
) {
    /**
     * Returns a signed PLC operation using [signature].
     */
    public fun signed(signature: String): PlcOperation =
        PlcOperation(
            sig = signature,
            prev = prev,
            type = type,
            services = services,
            alsoKnownAs = alsoKnownAs,
            rotationKeys = rotationKeys,
            verificationMethods = verificationMethods,
        )
}

/**
 * PLC tombstone payload used to permanently deactivate a DID.
 *
 * @property sig Signature for the tombstone, or `null` before signing.
 * @property prev CID of the prior PLC entry.
 * @property type PLC directory discriminator. Defaults to [TYPE].
 */
@Serializable
public data class PlcTombstone(
    override val sig: String? = null,
    override val prev: String,
    override val type: String = TYPE,
) : PlcLogEntry {
    /**
     * Returns the unsigned tombstone payload.
     */
    public fun unsigned(): PlcUnsignedTombstone = PlcUnsignedTombstone(prev = prev, type = type)

    /**
     * Returns a copy of this tombstone with [signature].
     */
    public fun withSignature(signature: String): PlcTombstone = copy(sig = signature)

    public companion object {
        /**
         * PLC directory discriminator for tombstones.
         */
        public const val TYPE: String = "plc_tombstone"
    }
}

/**
 * Unsigned PLC tombstone payload used for deterministic encoding and signing.
 *
 * @property prev CID of the prior PLC entry.
 * @property type PLC directory discriminator. Defaults to [PlcTombstone.TYPE].
 */
@Serializable
public data class PlcUnsignedTombstone(
    val prev: String,
    val type: String = PlcTombstone.TYPE,
) {
    /**
     * Returns a signed PLC tombstone using [signature].
     */
    public fun signed(signature: String): PlcTombstone = PlcTombstone(sig = signature, prev = prev, type = type)
}

/**
 * PLC service entry stored inside an operation.
 *
 * @property type Service type, for example `AtprotoPersonalDataServer`.
 * @property endpoint Service endpoint URL.
 */
@Serializable
public data class PlcService(
    val type: String,
    val endpoint: String,
)

/**
 * Indexed PLC directory entry returned by `/log/audit` and `/export`.
 *
 * @property did DID the entry belongs to.
 * @property operation PLC entry payload.
 * @property cid CID assigned to the entry.
 * @property nullified Whether a later fork nullified the entry.
 * @property createdAt ISO-8601 timestamp when the directory indexed the entry.
 */
@Serializable
public data class PlcIndexedOperation(
    val did: AtprotoDid,
    val operation: PlcLogEntry,
    val cid: String,
    val nullified: Boolean,
    @SerialName("createdAt")
    val createdAt: String,
)

internal object PlcLogEntrySerializer : JsonContentPolymorphicSerializer<PlcLogEntry>(PlcLogEntry::class) {
    override fun selectDeserializer(element: JsonElement) =
        when (element.jsonObject["type"]?.jsonPrimitive?.content) {
            PlcOperation.TYPE -> PlcOperation.serializer()
            PlcTombstone.TYPE -> PlcTombstone.serializer()
            else -> throw SerializationException("Unsupported PLC log entry type")
        }
}
