package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportSchemaVersion

/**
 * Thrown when an archive's major version exceeds the app's supported version.
 */
class UnsupportedExportVersionException(
    val archiveVersion: ExportSchemaVersion,
) : Exception(
        "Export version $archiveVersion is not supported. " +
            "This app supports up to version ${ExportSchemaVersion.CURRENT}.",
    )
