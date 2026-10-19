package app.logdate.client.sync.space

/** Where the server stores an account's data. */
enum class AccountStorageMode {
    /** The per-kind sync endpoints. */
    LEGACY,

    /** The account's sealed personal space. */
    PERSONAL_SPACE_V1,
}

/** Reads the signed-in account's storage mode, as the server reports it. */
fun interface AccountStorageModeSource {
    /** The mode, or null when the server hasn't reported one. */
    suspend fun currentMode(): AccountStorageMode?

    companion object {
        /**
         * Until the account endpoint reports a storage mode, no account is known to be stored in a
         * personal space, so the device keeps using the per-kind endpoints.
         */
        val NotReported: AccountStorageModeSource = AccountStorageModeSource { null }
    }
}

/**
 * Decides whether this device syncs through the personal space. All three must agree: this build
 * turns the transport on, the connected server offers personal spaces, and the server stores the
 * signed-in account there. The device never guesses the mode, because every device of an account
 * must use the same transport.
 */
class SpaceTransportGate(
    private val enabledInThisBuild: suspend () -> Boolean,
    private val serverOffersPersonalSpace: () -> Boolean,
    private val storageMode: AccountStorageModeSource,
) {
    suspend fun isOpen(): Boolean =
        enabledInThisBuild() &&
            serverOffersPersonalSpace() &&
            storageMode.currentMode() == AccountStorageMode.PERSONAL_SPACE_V1

    companion object {
        val Closed: SpaceTransportGate = SpaceTransportGate({ false }, { false }, AccountStorageModeSource.NotReported)
    }
}
