package app.logdate.shared.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How a server keeps one account's journal data. Every device of the account syncs the same way, so
 * the server decides it per account and every app reads it from the account, never from a guess.
 *
 * On the wire it is the string in [wireValue]. A server that predates storage modes sends nothing,
 * which means [LEGACY]. A value this app doesn't know reads as [UNRECOGNIZED] instead of failing
 * the whole account, and so does a mode the device hasn't heard from the server yet: an app must not
 * sync an account whose mode it doesn't recognize, and shows why instead. The server refuses the
 * legacy sync routes to accounts in any other mode with [StorageModeErrorCodes.SYNC_STORAGE_MODE_MISMATCH].
 *
 * This is the one storage mode type for every module: the server, the sync layer and its transports.
 */
@Serializable(with = AccountStorageModeSerializer::class)
enum class AccountStorageMode(
    val wireValue: String,
) {
    /** One REST endpoint, cursor and wire format per kind of data. */
    LEGACY("legacy"),

    /** Every kind of data as sealed `app.logdate.*` records in the account's personal space. */
    PERSONAL_SPACE_V1(ServerProtocolFeature.PERSONAL_SPACE_V1),

    /** A mode added after this app was built. */
    UNRECOGNIZED("unrecognized"),
    ;

    companion object {
        /** The mode [value] names, or [UNRECOGNIZED] when it names none this app knows. */
        fun fromWireValue(value: String): AccountStorageMode =
            entries.firstOrNull { it != UNRECOGNIZED && it.wireValue == value } ?: UNRECOGNIZED
    }
}

/** Writes [AccountStorageMode.wireValue] and reads any string, mapping unknown ones to [AccountStorageMode.UNRECOGNIZED]. */
object AccountStorageModeSerializer : KSerializer<AccountStorageMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.logdate.shared.model.AccountStorageMode", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AccountStorageMode,
    ) = encoder.encodeString(value.wireValue)

    override fun deserialize(decoder: Decoder): AccountStorageMode = AccountStorageMode.fromWireValue(decoder.decodeString())
}

/** Error codes the server sends about an account's storage mode. */
object StorageModeErrorCodes {
    /**
     * A legacy sync route was called for an account whose data isn't kept in legacy storage. Read the
     * account again from `/auth/me` and sync the way its `storageMode` says.
     */
    const val SYNC_STORAGE_MODE_MISMATCH = "SYNC_STORAGE_MODE_MISMATCH"
}
