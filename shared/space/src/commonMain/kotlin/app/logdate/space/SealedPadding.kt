package app.logdate.space

/**
 * Pads a sealed payload to a size bucket, so a record's size doesn't reveal what kind of record
 * it is. Layout: a 4-byte big-endian length, the payload, then zeros. Buckets are powers of two
 * from 512 bytes up to 64 KiB, then whole multiples of 64 KiB.
 */
public object SealedPadding {
    /** The largest ciphertext the `app.logdate.crypto.sealed` schema allows. */
    public const val MAX_CIPHERTEXT: Int = 4_000_000
    private const val TAG = 16
    private const val HEADER = 4
    private const val SMALLEST = 512
    private const val STEP = 65_536

    /** The bucket a payload of [length] bytes is padded to, including the length header. */
    public fun paddedSize(length: Int): Int {
        require(length in 0..MAX_CIPHERTEXT) { "A sealed payload's length must be between 0 and $MAX_CIPHERTEXT" }
        val needed = length + HEADER
        if (needed > STEP) return ((needed + STEP - 1) / STEP) * STEP
        var size = SMALLEST
        while (size < needed) size *= 2
        return size
    }

    /** Whether [size] is a size [pad] can produce. */
    public fun isPaddedSize(size: Int): Boolean {
        if (size !in SMALLEST..(MAX_CIPHERTEXT - TAG)) return false
        if (size <= STEP) return size and (size - 1) == 0
        return size % STEP == 0
    }

    /** Pads [payload] to its bucket. Throws [SealedRecordException] when it's too large to seal. */
    public fun pad(payload: ByteArray): ByteArray {
        if (payload.size > MAX_CIPHERTEXT - TAG - HEADER) throw SealedRecordException("The record is too large to seal")
        val size = paddedSize(payload.size)
        if (size + TAG > MAX_CIPHERTEXT) throw SealedRecordException("The record is too large to seal")
        val out = ByteArray(size)
        out[0] = (payload.size ushr 24).toByte()
        out[1] = (payload.size ushr 16).toByte()
        out[2] = (payload.size ushr 8).toByte()
        out[3] = payload.size.toByte()
        payload.copyInto(out, HEADER)
        return out
    }

    /**
     * Returns the payload inside [padded]. Throws [SealedRecordException] unless [padded] is
     * exactly what [pad] produces: a bucket size, a length that fits, and zeros after the payload.
     */
    public fun unpad(padded: ByteArray): ByteArray {
        if (!isPaddedSize(padded.size)) throw SealedRecordException("The sealed payload isn't a padded size")
        val length =
            ((padded[0].toInt() and 0xff) shl 24) or ((padded[1].toInt() and 0xff) shl 16) or
                ((padded[2].toInt() and 0xff) shl 8) or (padded[3].toInt() and 0xff)
        if (length < 0 || length > padded.size - HEADER) throw SealedRecordException("The sealed payload length is invalid")
        if (paddedSize(length) != padded.size) throw SealedRecordException("The sealed payload is in the wrong size bucket")
        for (index in HEADER + length until padded.size) {
            if (padded[index] != 0.toByte()) throw SealedRecordException("The sealed payload's padding isn't zeros")
        }
        return padded.copyOfRange(HEADER, HEADER + length)
    }
}
