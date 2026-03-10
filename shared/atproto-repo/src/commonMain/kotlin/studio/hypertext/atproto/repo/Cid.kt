package studio.hypertext.atproto.repo

import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString
import kotlin.jvm.JvmInline

/**
 * Minimal CID wrapper used by the standalone repo runtime.
 */
@JvmInline
@Serializable
public value class Cid(
    public val value: String,
) {
    init {
        require(value.startsWith(MULTIBASE_BASE32_PREFIX)) { "CID values must use lowercase multibase base32" }
    }

    override fun toString(): String = value

    public companion object {
        /**
         * Parses [value] into a [Cid].
         */
        public fun parse(value: String): Result<Cid> = runCatching { Cid(value.trim()) }

        /**
         * Parses [value] into a [Cid] or throws.
         */
        public fun require(value: String): Cid = Cid(value.trim())

        /**
         * Creates a CID using [codec] and a SHA-256 digest of [bytes].
         */
        public fun sha256(
            codec: Int,
            bytes: ByteArray,
        ): Cid {
            val digest = bytes.toByteString().sha256().toByteArray()
            val cidBytes =
                encodeVarint(CID_VERSION) +
                    encodeVarint(codec) +
                    byteArrayOf(SHA256_MULTIHASH_CODE.toByte(), SHA256_DIGEST_SIZE.toByte()) +
                    digest
            return Cid("$MULTIBASE_BASE32_PREFIX${encodeBase32(cidBytes)}")
        }

        /**
         * Creates a CID for raw blob bytes using the AT Protocol `raw` codec.
         */
        public fun rawSha256(bytes: ByteArray): Cid = sha256(codec = RAW_CODEC, bytes = bytes)
    }
}

internal fun encodeVarint(value: Int): ByteArray {
    var remaining = value
    val bytes = mutableListOf<Byte>()
    do {
        var next = remaining and 0x7f
        remaining = remaining ushr 7
        if (remaining != 0) {
            next = next or 0x80
        }
        bytes += next.toByte()
    } while (remaining != 0)
    return bytes.toByteArray()
}

internal fun encodeBase32(bytes: ByteArray): String {
    if (bytes.isEmpty()) {
        return ""
    }
    val output = StringBuilder(((bytes.size * 8) + 4) / 5)
    var buffer = 0
    var bitsLeft = 0
    bytes.forEach { byte ->
        buffer = (buffer shl 8) or (byte.toInt() and 0xff)
        bitsLeft += 8
        while (bitsLeft >= 5) {
            output.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1f])
            bitsLeft -= 5
        }
    }
    if (bitsLeft > 0) {
        output.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
    }
    return output.toString()
}

private const val CID_VERSION: Int = 1
public const val RAW_CODEC: Int = 0x55
public const val DAG_CBOR_CODEC: Int = 0x71
private const val SHA256_MULTIHASH_CODE: Int = 0x12
private const val SHA256_DIGEST_SIZE: Int = 32
private const val MULTIBASE_BASE32_PREFIX: Char = 'b'
private const val BASE32_ALPHABET: String = "abcdefghijklmnopqrstuvwxyz234567"
