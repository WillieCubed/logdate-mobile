package app.logdate.client.domain.export.archive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * The location of a file inside an export archive, relative to the archive root.
 *
 * Every reference to a file in the export uses this type, so a device path or content URI cannot be
 * written into an archive by accident. A path uses `/` as the separator and cannot be absolute,
 * climb out with `..`, name a drive or URI scheme, or contain characters that common file systems
 * reject. Reading an archive validates each path the same way, since an archive is untrusted input.
 */
@Serializable(with = ArchivePathSerializer::class)
@JvmInline
value class ArchivePath private constructor(
    val value: String,
) {
    /** The folder containing this file, or an empty string for a file at the archive root. */
    val directory: String get() = value.substringBeforeLast('/', "")

    val fileName: String get() = value.substringAfterLast('/')

    override fun toString(): String = value

    companion object {
        /** Longest whole path, chosen so an extracted archive stays under Windows' 260 character limit. */
        const val MAX_PATH_LENGTH = 120

        const val MAX_SEGMENT_LENGTH = 64

        private const val FORBIDDEN_CHARACTERS = "\\:*?\"<>|"

        fun parse(raw: String): ArchivePath? {
            if (raw.isEmpty() || raw.length > MAX_PATH_LENGTH) return null
            if (raw.startsWith('/') || raw.endsWith('/')) return null
            if (!raw.split('/').all(::isValidSegment)) return null
            return ArchivePath(raw)
        }

        fun of(raw: String): ArchivePath = parse(raw) ?: throw IllegalArgumentException("Not a valid archive path")

        private fun isValidSegment(segment: String): Boolean {
            if (segment.isEmpty() || segment == "." || segment == ".." || segment.length > MAX_SEGMENT_LENGTH) return false
            if (segment.last() == '.' || segment.last() == ' ') return false
            return segment.none { it.isISOControl() || it in FORBIDDEN_CHARACTERS }
        }
    }
}

internal object ArchivePathSerializer : KSerializer<ArchivePath> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ArchivePath", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ArchivePath,
    ) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): ArchivePath =
        ArchivePath.parse(decoder.decodeString()) ?: throw SerializationException("Not a valid archive path")
}
