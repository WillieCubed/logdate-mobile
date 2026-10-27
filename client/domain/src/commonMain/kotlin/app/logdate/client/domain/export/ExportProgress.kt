package app.logdate.client.domain.export

sealed class ExportProgress {
    data object Starting : ExportProgress()

    data class InProgress(
        val percentage: Float,
        val stage: ExportStage,
    ) : ExportProgress()

    data class Completed(
        val result: ExportResult,
    ) : ExportProgress()

    data class Failed(
        val error: ExportError,
    ) : ExportProgress()
}

/**
 * Typed export progress stages. The presentation layer resolves
 * these to localized strings.
 */
enum class ExportStage {
    COLLECTING_JOURNALS,
    COLLECTING_NOTES,
    COLLECTING_DRAFTS,
    PREPARING_DATA,
    WRITING_ARCHIVE,
}

/**
 * Typed export errors. The presentation layer resolves these
 * to localized user-facing messages.
 */
enum class ExportError {
    UNKNOWN,
}
