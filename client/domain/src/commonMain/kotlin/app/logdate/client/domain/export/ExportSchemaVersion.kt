package app.logdate.client.domain.export

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Semantic version for the LogDate export schema.
 *
 * **Versioning policy:**
 * - Minor version bump (e.g. 1.0 → 1.1): additive-only changes. All new fields
 *   must have default values so older importers still parse the JSON successfully
 *   via `ignoreUnknownKeys`.
 * - Major version bump (e.g. 1.x → 2.0): structural/breaking change. Requires
 *   a migration path in [app.logdate.client.domain.restore.ExportMigrations] and,
 *   if the old shape is fundamentally different, legacy model classes.
 */
@Serializable(with = ExportSchemaVersionSerializer::class)
data class ExportSchemaVersion(
    val major: Int,
    val minor: Int,
) : Comparable<ExportSchemaVersion> {
    override fun compareTo(other: ExportSchemaVersion): Int {
        val majorCmp = major.compareTo(other.major)
        return if (majorCmp != 0) majorCmp else minor.compareTo(other.minor)
    }

    override fun toString(): String = "$major.$minor"

    companion object {
        val V1_0 = ExportSchemaVersion(1, 0)
        val V1_1 = ExportSchemaVersion(1, 1)
        val V1_2 = ExportSchemaVersion(1, 2)

        /** The schema version written by the current app. */
        val CURRENT = V1_2

        fun parse(value: String): ExportSchemaVersion {
            val parts = value.split(".")
            require(parts.size == 2) { "Invalid export schema version format: $value" }
            return ExportSchemaVersion(parts[0].toInt(), parts[1].toInt())
        }
    }
}

internal object ExportSchemaVersionSerializer : KSerializer<ExportSchemaVersion> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExportSchemaVersion", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ExportSchemaVersion,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): ExportSchemaVersion = ExportSchemaVersion.parse(decoder.decodeString())
}
