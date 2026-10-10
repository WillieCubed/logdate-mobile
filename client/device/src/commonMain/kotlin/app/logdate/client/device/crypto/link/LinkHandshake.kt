package app.logdate.client.device.crypto.link

/** One side's single-use X25519 key pair for one link exchange. */
class LinkKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
)

/**
 * The keys both devices derive from one exchange.
 *
 * @property bundleKey Seals the link bundle with AES-256-GCM.
 * @property confirmKey Keys the watch's HMAC confirming it opened the bundle.
 * @property pairKey Stored by both devices after a pair-only link to authenticate later requests.
 */
class LinkKeys(
    val bundleKey: ByteArray,
    val confirmKey: ByteArray,
    val pairKey: ByteArray,
)

class SealedBytes(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/** The exchange cannot continue: a bad key, a changed transcript, or a bundle that fails its check. */
class LinkHandshakeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object LinkLabels {
    const val TRANSCRIPT = "LDLINK1"
    const val BUNDLE = "LDLINK1 bundle"
    const val CONFIRM = "LDLINK1 confirm"
    const val PAIR = "LDLINK1 pair"
}

/**
 * Everything both devices saw in one exchange. Its hash salts every key and authenticates the bundle,
 * so keys from one exchange never open anything from another, and a changed field fails the exchange.
 */
class LinkTranscript(
    val phoneNode: String,
    val watchNode: String,
    val phonePublicKey: ByteArray,
    val watchPublicKey: ByteArray,
    val phoneNonce: ByteArray,
    val watchNonce: ByteArray,
    val schema: Int,
) {
    /**
     * [LinkLabels.TRANSCRIPT], then each field as a 4-byte big-endian length and its bytes, then
     * [schema] as a 4-byte big-endian integer. The lengths keep "ab" + "c" apart from "a" + "bc".
     */
    fun encode(): ByteArray {
        val fields =
            listOf(
                phoneNode.encodeToByteArray(),
                watchNode.encodeToByteArray(),
                phonePublicKey,
                watchPublicKey,
                phoneNonce,
                watchNonce,
            )
        val prefixed = fields.fold(LinkLabels.TRANSCRIPT.encodeToByteArray()) { encoded, field -> encoded + int32(field.size) + field }
        return prefixed + int32(schema)
    }

    private fun int32(value: Int): ByteArray =
        byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
}

/**
 * The key exchange that links a watch to its phone: X25519, then HKDF-SHA256 salted with the
 * transcript hash, AES-256-GCM for the bundle, and HMAC-SHA256 for the watch's confirmation.
 */
interface LinkHandshake {
    fun generateKeyPair(): LinkKeyPair

    fun newNonce(): ByteArray

    fun transcriptHash(transcript: LinkTranscript): ByteArray

    /** Throws [LinkHandshakeException] for a key of the wrong size or one that gives no shared secret. */
    fun deriveKeys(
        ownPrivateKey: ByteArray,
        peerPublicKey: ByteArray,
        transcriptHash: ByteArray,
    ): LinkKeys

    fun seal(
        bundleKey: ByteArray,
        plaintext: ByteArray,
        transcriptHash: ByteArray,
    ): SealedBytes

    /** Throws [LinkHandshakeException] when the key, IV, ciphertext or transcript differ from sealing. */
    fun open(
        bundleKey: ByteArray,
        sealed: SealedBytes,
        transcriptHash: ByteArray,
    ): ByteArray

    fun confirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
    ): ByteArray

    fun verifyConfirmation(
        confirmKey: ByteArray,
        transcriptHash: ByteArray,
        mac: ByteArray,
    ): Boolean
}
