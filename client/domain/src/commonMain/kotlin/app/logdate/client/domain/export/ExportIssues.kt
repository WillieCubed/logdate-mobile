package app.logdate.client.domain.export

import io.github.aakira.napier.Napier

enum class ExportIssueCode {
    JOURNALS_UNAVAILABLE,
    NOTES_UNAVAILABLE,
    DRAFTS_UNAVAILABLE,
    APP_INFO_UNAVAILABLE,
    DEVICE_ID_UNAVAILABLE,
    USER_ID_UNAVAILABLE,
    PROFILE_UNAVAILABLE,
    PLACES_UNAVAILABLE,
    LOCATION_HISTORY_UNAVAILABLE,
    ASSOCIATIONS_UNAVAILABLE,
    MEDIA_MANIFEST_UNAVAILABLE,
    MEDIA_RECOVERED_NORMALIZED_PATH,
    MEDIA_RECOVERED_APP_PRIVATE_AUDIO,
    MEDIA_RECOVERED_APP_PRIVATE_MEDIA,
    MEDIA_RECOVERED_MEDIA_STORE,
    MEDIA_BYTES_MISSING,
}

data class ExportIssue(
    val code: ExportIssueCode,
    val source: String? = null,
    val detail: String? = null,
)

internal fun MutableList<ExportIssue>.record(
    code: ExportIssueCode,
    throwable: Throwable,
    source: String? = null,
) {
    val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
    add(ExportIssue(code = code, source = source, detail = detail))
    Napier.w("Export issue recorded: ${code.name}", throwable)
}

internal fun ExportIssue.describe(): String =
    when (code) {
        ExportIssueCode.JOURNALS_UNAVAILABLE ->
            "Journals could not be loaded. The export used an empty journals section."
        ExportIssueCode.NOTES_UNAVAILABLE ->
            "Notes could not be loaded. The export used an empty notes section."
        ExportIssueCode.DRAFTS_UNAVAILABLE ->
            "Drafts could not be loaded. The export used an empty drafts section."
        ExportIssueCode.APP_INFO_UNAVAILABLE ->
            "App version metadata could not be read. Default export metadata was written instead."
        ExportIssueCode.DEVICE_ID_UNAVAILABLE ->
            "Device ID metadata could not be read. A fallback device identifier was written instead."
        ExportIssueCode.USER_ID_UNAVAILABLE ->
            "User identity metadata could not be read. A fallback user identifier was written instead."
        ExportIssueCode.PROFILE_UNAVAILABLE ->
            "Profile data could not be loaded. The export omitted the profile section."
        ExportIssueCode.PLACES_UNAVAILABLE ->
            "Places could not be loaded. The export omitted the places section."
        ExportIssueCode.LOCATION_HISTORY_UNAVAILABLE ->
            "Location history could not be loaded. The export omitted the location history section."
        ExportIssueCode.ASSOCIATIONS_UNAVAILABLE ->
            "Journal associations could not be loaded. The export wrote an empty journal_notes section."
        ExportIssueCode.MEDIA_MANIFEST_UNAVAILABLE ->
            "Media manifest preparation failed. Media entries may be incomplete."
        ExportIssueCode.MEDIA_RECOVERED_NORMALIZED_PATH ->
            "Recovered media by normalizing a stale file path${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_AUDIO ->
            "Recovered audio from app-private recording storage${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_APP_PRIVATE_MEDIA ->
            "Recovered media from app-private storage${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_RECOVERED_MEDIA_STORE ->
            "Recovered media from MediaStore${sourceSuffix(source)}"
        ExportIssueCode.MEDIA_BYTES_MISSING ->
            "Media bytes could not be exported${sourceSuffix(source)}"
    }

private fun sourceSuffix(source: String?): String =
    source?.let {
        ": $it"
    } ?: "."
