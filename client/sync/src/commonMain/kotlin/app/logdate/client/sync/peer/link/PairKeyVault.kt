package app.logdate.client.sync.peer.link

import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes

/**
 * Where a device stores the pair key from its last link with each peer. Later requests between the two
 * devices can be authenticated with it; it never leaves the device.
 */
interface PairKeyVault {
    suspend fun put(
        peer: String,
        key: ByteArray,
    )

    suspend fun get(peer: String): ByteArray?

    suspend fun remove(peer: String)
}

class SecureStoragePairKeyVault(
    private val secureStorage: SecureStorage,
    private val prefix: String,
) : PairKeyVault {
    override suspend fun put(
        peer: String,
        key: ByteArray,
    ) = secureStorage.putBytes(prefix + peer, key)

    override suspend fun get(peer: String): ByteArray? = secureStorage.getBytes(prefix + peer)

    override suspend fun remove(peer: String) = secureStorage.remove(prefix + peer)
}
