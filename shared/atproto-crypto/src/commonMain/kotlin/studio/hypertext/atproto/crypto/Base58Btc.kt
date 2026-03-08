package studio.hypertext.atproto.crypto

private const val BASE_58: Int = 58
private const val BASE_256: Int = 256
private const val ZERO_ENCODED_CHAR: Char = '1'
private const val ASCII_TABLE_SIZE: Int = 128
private val alphabet: CharArray = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
private val alphabetIndexes: IntArray =
    IntArray(ASCII_TABLE_SIZE) { -1 }.also { indexes ->
        alphabet.forEachIndexed { index, char ->
            indexes[char.code] = index
        }
    }

/**
 * Base58btc encoder and decoder used by multibase and multikey values.
 */
public object Base58Btc {
    /**
     * Encodes [bytes] into a base58btc string.
     */
    public fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }

        val leadingZeroCount = bytes.countLeadingZeros()
        if (leadingZeroCount == bytes.size) {
            return ZERO_ENCODED_CHAR.toString().repeat(leadingZeroCount)
        }

        val digits = IntArray((bytes.size - leadingZeroCount) * 2)
        var digitCount = 0

        for (byte in bytes.copyOfRange(leadingZeroCount, bytes.size)) {
            var carry = byte.toInt() and 0xff
            for (index in 0 until digitCount) {
                val value = (digits[index] * BASE_256) + carry
                digits[index] = value % BASE_58
                carry = value / BASE_58
            }
            while (carry > 0) {
                digits[digitCount++] = carry % BASE_58
                carry /= BASE_58
            }
        }

        return buildString(leadingZeroCount + digitCount) {
            repeat(leadingZeroCount) {
                append(ZERO_ENCODED_CHAR)
            }
            for (index in digitCount - 1 downTo 0) {
                append(alphabet[digits[index]])
            }
        }
    }

    /**
     * Decodes a base58btc [value] into raw bytes.
     *
     * @throws IllegalArgumentException when [value] contains non-base58btc characters.
     */
    public fun decode(value: String): ByteArray {
        if (value.isEmpty()) {
            return ByteArray(0)
        }

        val leadingZeroCount = value.countLeadingOnes()
        if (leadingZeroCount == value.length) {
            return ByteArray(leadingZeroCount)
        }

        val decodedChars = value.substring(leadingZeroCount)
        val bytes = IntArray(decodedChars.length * 2)
        var byteCount = 0

        decodedChars.forEach { char ->
            require(char.code < ASCII_TABLE_SIZE && alphabetIndexes[char.code] >= 0) {
                "Invalid base58btc value"
            }
            var carry = alphabetIndexes[char.code]
            for (index in 0 until byteCount) {
                val converted = (bytes[index] * BASE_58) + carry
                bytes[index] = converted and 0xff
                carry = converted ushr 8
            }
            while (carry > 0) {
                bytes[byteCount++] = carry and 0xff
                carry = carry ushr 8
            }
        }

        return ByteArray(leadingZeroCount + byteCount).also { decoded ->
            for (index in 0 until byteCount) {
                decoded[decoded.lastIndex - index] = bytes[index].toByte()
            }
        }
    }
}

private fun ByteArray.countLeadingZeros(): Int {
    var count = 0
    for (byte in this) {
        if (byte.toInt() != 0) {
            break
        }
        count++
    }
    return count
}

private fun String.countLeadingOnes(): Int {
    var count = 0
    for (char in this) {
        if (char != ZERO_ENCODED_CHAR) {
            break
        }
        count++
    }
    return count
}
