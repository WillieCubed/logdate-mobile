package app.logdate.client.device.crypto

/**
 * Derives per-content encryption keys using HKDF-SHA256.
 *
 * Starting from the identity key (root), we derive unique keys for different content types
 * and individual items using a deterministic process. This ensures:
 *
 * - Different content types use different keys (confining damage of key compromise)
 * - Different items use different keys (preventing pattern analysis)
 * - Keys are deterministic (same input always produces same key)
 * - Keys are independent (compromising one doesn't help with others)
 */
class KeyDerivation(private val cryptoManager: CryptoManager) {
    
    /**
     * Derives a content-specific encryption key using HKDF.
     *
     * @param identityKey The master identity key (32 bytes)
     * @param context The context label (e.g., "journal_entry", "media_file")
     * @param contentId The unique content identifier (e.g., entry UUID)
     * @return A derived AES-256 key (32 bytes)
     */
    fun deriveKey(
        identityKey: ByteArray,
        context: String,
        contentId: String
    ): ByteArray {
        require(identityKey.size == 32) { "Identity key must be 32 bytes" }
        
        val salt = contentId.encodeToByteArray()
        val info = context.encodeToByteArray()
        
        val prk = hkdfExtract(identityKey, salt)
        return hkdfExpand(prk, info, length = 32)
    }
    
    /**
     * HKDF-Extract step using HMAC-SHA256.
     * Produces a pseudo-random key (PRK) from input key material and salt.
     */
    private fun hkdfExtract(ikm: ByteArray, salt: ByteArray): ByteArray {
        // If salt is empty, use a salt of HashLen zero bytes
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        return hmacSha256(actualSalt, ikm)
    }
    
    /**
     * HKDF-Expand step using HMAC-SHA256.
     * Expands PRK into desired number of output bytes.
     */
    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * 32) { "Output length too large (max ${255 * 32} bytes)" }
        
        val result = ByteArray(length)
        var offset = 0
        var counter: Byte = 1
        var t = ByteArray(0)
        
        while (offset < length) {
            val input = t + info + byteArrayOf(counter)
            t = hmacSha256(prk, input)
            
            val copyLength = minOf(t.size, length - offset)
            t.copyInto(result, offset, 0, copyLength)
            offset += copyLength
            counter++
        }
        
        return result
    }
    
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        return cryptoManager.hmacSha256(key, data)
    }
}
