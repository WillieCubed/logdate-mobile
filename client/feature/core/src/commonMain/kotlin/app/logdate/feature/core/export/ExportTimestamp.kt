package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Formats an [Instant] as `YYYY-MM-DD_HH-MM` for use in export filenames.
 */
internal fun Instant.toExportTimestamp(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(local.year)
        append('-')
        append(
            local.month.number
                .toString()
                .padStart(2, '0'),
        )
        append('-')
        append(
            local.day
                .toString()
                .padStart(2, '0'),
        )
        append('_')
        append(
            local.hour
                .toString()
                .padStart(2, '0'),
        )
        append('-')
        append(
            local.minute
                .toString()
                .padStart(2, '0'),
        )
    }
}

/**
 * Returns a timestamped export filename like `logdate_export_2025-03-27_14-30.zip`.
 */
internal fun generateExportFileName(): String {
    val timestamp = Clock.System.now().toExportTimestamp()
    return "logdate_export_$timestamp.${ExportFormat.FILE_EXTENSION}"
}

/**
 * Returns a timestamped encrypted backup filename like `logdate_backup_2025-03-27_14-30.ldb`.
 */
internal fun generateEncryptedBackupFileName(): String {
    val timestamp = Clock.System.now().toExportTimestamp()
    return "logdate_backup_$timestamp.${ExportFormat.ENCRYPTED_BACKUP_FILE_EXTENSION}"
}
