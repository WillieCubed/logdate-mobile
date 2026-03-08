package studio.hypertext.atproto.crypto

/**
 * Multibase and multicodec helpers for AT Protocol public keys.
 */
public object Multikey {
    /**
     * P-256 public key multicodec prefix.
     *
     * The remaining bytes are the SEC1 compressed public key.
     */
    public val p256PublicKeyPrefix: ByteArray = byteArrayOf(0x80.toByte(), 0x24)

    /**
     * Ed25519 public key multicodec prefix.
     */
    public val ed25519PublicKeyPrefix: ByteArray = byteArrayOf(0xed.toByte(), 0x01)

    /**
     * Encodes [keyBytes] with [multicodecPrefix] as a multibase base58btc value.
     */
    public fun encode(
        multicodecPrefix: ByteArray,
        keyBytes: ByteArray,
    ): String = "z${Base58Btc.encode(multicodecPrefix + keyBytes)}"

    /**
     * Encodes a compressed P-256 public key as a multikey value.
     *
     * @throws IllegalArgumentException when [compressedKey] is not a compressed SEC1 P-256 key.
     */
    public fun encodeP256PublicKey(compressedKey: ByteArray): String {
        require(compressedKey.size == P256_COMPRESSED_KEY_BYTES) {
            "Compressed P-256 public keys must be 33 bytes"
        }
        val prefix = compressedKey.first().toInt() and 0xff
        require(prefix == COMPRESSED_EVEN_PREFIX || prefix == COMPRESSED_ODD_PREFIX) {
            "Compressed P-256 public keys must start with 0x02 or 0x03"
        }
        return encode(p256PublicKeyPrefix, compressedKey)
    }

    /**
     * Decodes a multibase base58btc [value] into raw multicodec-prefixed bytes.
     *
     * @throws IllegalArgumentException when [value] is not a base58btc multibase string.
     */
    public fun decode(value: String): ByteArray {
        require(value.startsWith(MULTIBASE_BASE58_BTC_PREFIX)) {
            "Only base58btc multibase values are supported"
        }
        return Base58Btc.decode(value.removePrefix(MULTIBASE_BASE58_BTC_PREFIX))
    }

    private const val MULTIBASE_BASE58_BTC_PREFIX: String = "z"
    private const val P256_COMPRESSED_KEY_BYTES: Int = 33
    private const val COMPRESSED_EVEN_PREFIX: Int = 0x02
    private const val COMPRESSED_ODD_PREFIX: Int = 0x03
}
