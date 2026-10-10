package app.logdate.client.device.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A short, non-reversible marker of an identity key: enough to tell two keys apart, never enough to
 * recover one. Every synced value includes it, and a linked device checks the key its phone sent
 * against it, so its output must never change.
 */
@OptIn(ExperimentalEncodingApi::class)
object IdentityKeyFingerprint {
    private val CONTEXT = "sync-payload-fingerprint".encodeToByteArray()
    private const val BYTES = 8

    fun of(
        identityKey: ByteArray,
        cryptoManager: CryptoManager,
    ): String = Base64.encode(cryptoManager.hmacSha256(identityKey, CONTEXT).copyOf(BYTES))
}
