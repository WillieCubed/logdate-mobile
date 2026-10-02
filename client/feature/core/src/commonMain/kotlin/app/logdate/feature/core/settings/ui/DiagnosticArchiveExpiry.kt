package app.logdate.feature.core.settings.ui

internal data class PrivateDiagnosticArchive(
    val name: String,
    val modifiedAtMillis: Long,
)

internal const val DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS = 30 * 60 * 1000L

internal fun expiredDiagnosticArchives(
    nowMillis: Long,
    files: List<PrivateDiagnosticArchive>,
): List<String> =
    files
        .filter { file ->
            file.name.startsWith("report-") &&
                file.name.endsWith(".zip") &&
                file.name.none { it == '/' || it == '\\' } &&
                diagnosticArchiveExpiryDelay(nowMillis, file.modifiedAtMillis) == 0L
        }.map { it.name }

internal fun diagnosticArchiveExpiryDelay(
    nowMillis: Long,
    modifiedAtMillis: Long,
): Long {
    if (modifiedAtMillis < 0L) return 0L
    if (nowMillis <= modifiedAtMillis) return DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS
    val age = nowMillis - modifiedAtMillis
    return (DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS - age).coerceAtLeast(0L)
}
