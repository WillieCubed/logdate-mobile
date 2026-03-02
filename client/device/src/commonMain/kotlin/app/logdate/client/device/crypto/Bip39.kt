package app.logdate.client.device.crypto

/**
 * BIP-39 Mnemonic utilities for generating and validating recovery phrases.
 */
object Bip39 {
    fun generateMnemonic(entropy: ByteArray): List<String> {
        require(entropy.size == 16) { "Entropy must be 16 bytes for 12-word mnemonic" }

        val checksum = calculateChecksum(entropy)
        val checksumBits = (checksum[0].toInt() and 0xFF) ushr 4

        val entropyBits = bytesToBinary(entropy)
        val checksumBitString = checksumBits.toString(2).padStart(4, '0')
        val allBits = entropyBits + checksumBitString

        return (0 until 12).map { i ->
            val start = i * 11
            val end = start + 11
            val bits = allBits.substring(start, end)
            val index = bits.toInt(2)
            BIP39_WORDLIST[index]
        }
    }

    fun mnemonicToEntropy(words: List<String>): ByteArray {
        require(words.size == 12) { "Mnemonic must be 12 words" }

        val indices =
            words.map { word ->
                BIP39_WORDLIST.indexOf(word).also {
                    require(it >= 0) { "Invalid word in mnemonic: $word" }
                }
            }

        val allBits =
            indices.joinToString("") { index ->
                index.toString(2).padStart(11, '0')
            }

        val entropyBits = allBits.substring(0, 128)
        val checksumBits = allBits.substring(128, 132)

        val entropy = binaryToBytes(entropyBits)

        val expectedChecksum = (calculateChecksum(entropy)[0].toInt() and 0xFF) ushr 4
        val actualChecksum = checksumBits.toInt(2)
        require(expectedChecksum == actualChecksum) { "Invalid mnemonic checksum" }

        return entropy
    }

    fun validateMnemonic(words: List<String>): Boolean {
        if (words.size != 12) return false

        return try {
            mnemonicToEntropy(words)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun bytesToBinary(bytes: ByteArray): String =
        bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
        }

    private fun binaryToBytes(binary: String): ByteArray {
        require(binary.length % 8 == 0) { "Binary string length must be multiple of 8" }

        return ByteArray(binary.length / 8) { i ->
            val start = i * 8
            val end = start + 8
            binary.substring(start, end).toInt(2).toByte()
        }
    }

    private fun calculateChecksum(data: ByteArray): ByteArray = sha256(data)
}

internal expect fun Bip39.sha256(data: ByteArray): ByteArray

/**
 * Derives a seed from mnemonic using PBKDF2 (BIP-39 standard).
 */
internal expect fun Bip39.mnemonicToSeed(
    mnemonic: String,
    passphrase: String = "",
): ByteArray
