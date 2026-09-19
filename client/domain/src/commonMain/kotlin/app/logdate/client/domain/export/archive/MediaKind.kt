package app.logdate.client.domain.export.archive

/** The kind of media a file holds, which decides the folder it is filed under in the archive. */
enum class MediaKind(
    val folder: String,
) {
    PHOTO("photos"),
    VIDEO("videos"),
    AUDIO("audio"),
}
