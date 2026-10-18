package app.logdate.client.device.restore

/** Serializes a native restore generation with local media-identity reconciliation. */
interface NativeRestoreDeliveryGate {
    suspend fun <T> withLock(block: suspend () -> T): T
}
