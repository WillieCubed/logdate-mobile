package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Preview data extracted from an export archive's metadata.
 */
data class ArchivePreview(
    val version: ExportSchemaVersion,
    val exportDate: Instant,
    val appVersion: String,
    val stats: ExportStats,
) {
    val hasDrafts: Boolean get() = stats.draftCount > 0
    val hasMedia: Boolean get() = stats.mediaCount > 0
}

/**
 * Parses export archive metadata to produce a lightweight preview
 * without performing the full restore.
 */
class PreviewArchiveUseCase {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun preview(metadataJson: String): ArchivePreview {
        val metadata = json.decodeFromString<ExportMetadata>(metadataJson)
        return ArchivePreview(
            version = metadata.version,
            exportDate = metadata.exportDate,
            appVersion = metadata.appVersion,
            stats = metadata.stats,
        )
    }
}
