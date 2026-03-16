package app.logdate.wear.sync

/**
 * Abstraction over the Wear Data Layer API for testability.
 *
 * Wraps [com.google.android.gms.wearable.DataClient] and
 * [com.google.android.gms.wearable.CapabilityClient] operations.
 */
interface WearDataLayerClient {
    /**
     * Puts a data item at the given [path] with the provided key-value [data].
     * Returns true if the put succeeded.
     */
    suspend fun putDataItem(path: String, data: Map<String, String>): Boolean

    /**
     * Deletes the data item at the given [path].
     * Returns true if deletion succeeded.
     */
    suspend fun deleteDataItem(path: String): Boolean

    /**
     * Returns true if a connected node with the [capability] is reachable.
     */
    suspend fun isPhoneConnected(capability: String = PHONE_CAPABILITY): Boolean

    /**
     * Sends a raw file at [localFilePath] to the connected phone node
     * via a channel at the given [channelPath].
     * Returns true if the transfer completed.
     */
    suspend fun sendFile(channelPath: String, localFilePath: String): Boolean

    /**
     * Sends a message to the connected phone node at the given [path].
     * Returns true if the message was delivered.
     */
    suspend fun sendMessage(path: String, data: ByteArray = byteArrayOf()): Boolean

    companion object {
        const val PHONE_CAPABILITY = "logdate_phone_app"
    }
}
