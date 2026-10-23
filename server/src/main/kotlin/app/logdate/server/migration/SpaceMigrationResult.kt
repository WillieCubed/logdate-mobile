package app.logdate.server.migration

/** What a migration call produced, or why the server refused it. */
sealed interface SpaceMigrationResult<out T> {
    data class Done<T>(
        val value: T,
    ) : SpaceMigrationResult<T>

    /**
     * The call was refused and nothing changed. [code] is one of
     * [app.logdate.shared.model.sync.SpaceMigrationErrorCodes] or `MEDIA_NOT_FOUND`.
     */
    data class Refused(
        val code: String,
        val message: String,
    ) : SpaceMigrationResult<Nothing>
}

internal fun refused(
    code: String,
    message: String,
): SpaceMigrationResult.Refused = SpaceMigrationResult.Refused(code, message)
