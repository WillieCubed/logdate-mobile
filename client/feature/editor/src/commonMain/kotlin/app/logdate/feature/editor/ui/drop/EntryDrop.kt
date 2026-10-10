package app.logdate.feature.editor.ui.drop

/** One item in a drag from another app: a file reference, some text, or both. */
data class DroppedItem(
    val mimeType: String,
    val uri: String?,
    val text: String?,
)

/** Why a dropped, pasted, or keyboard file could not be added to the entry. */
enum class MediaImportProblem {
    /** The file is not a photo, video, or audio file LogDate can keep. */
    Unsupported,

    /** The file could not be read, for example because the other app withdrew access. */
    Unreadable,
}

/**
 * What adding dropped, pasted, or keyboard files did: [uris] are the copies in LogDate's store to
 * attach, and [problems] lists each file that could not be added, so the editor can say so.
 */
data class EntryMediaImport(
    val uris: List<String>,
    val problems: List<MediaImportProblem> = emptyList(),
)

/** What a drop adds to an entry: media to import into LogDate's store, and text to insert. */
data class EntryDrop(
    val mediaUris: List<String>,
    val texts: List<String>,
)

private val mediaPrefixes = listOf("image/", "video/", "audio/")

/** Whether a drag carrying [mimeType] can become part of an entry. */
fun acceptsDroppedMimeType(mimeType: String): Boolean = mimeType.startsWith("text/") || mediaPrefixes.any(mimeType::startsWith)

/** Splits a drop into media to import and text to insert, ignoring anything an entry cannot hold. */
fun routeDroppedItems(items: List<DroppedItem>): EntryDrop =
    EntryDrop(
        mediaUris = items.filter { item -> mediaPrefixes.any(item.mimeType::startsWith) }.mapNotNull { it.uri },
        texts = items.filter { it.mimeType.startsWith("text/") }.mapNotNull { it.text?.takeIf(String::isNotBlank) },
    )

/** The Paste item the text menu adds for media on the clipboard. */
enum class MediaPasteItem {
    /** The clipboard holds only media, so the text field's own Paste would do nothing. */
    Paste,

    /** The clipboard also holds text, which the text field's own Paste inserts. */
    PasteAsAttachment,
}

/**
 * Which media Paste item the text menu shows for a clipboard holding [clipMimeTypes], or null when
 * there is no photo, video, or audio to attach.
 *
 * A clip copied as a raw link (`text/uri-list`) does not name its file's type, and reading the clip
 * to find out would show Android's clipboard notice every time the menu opens. It gets Paste as
 * attachment, beside the text field's own Paste of the link, and the type is checked when chosen.
 */
fun mediaPasteItem(clipMimeTypes: List<String>): MediaPasteItem? {
    val hasMedia = clipMimeTypes.any { mime -> mediaPrefixes.any(mime::startsWith) }
    return when {
        hasMedia && clipMimeTypes.none { it.startsWith("text/") } -> MediaPasteItem.Paste
        hasMedia || URI_LIST_MIME_TYPE in clipMimeTypes -> MediaPasteItem.PasteAsAttachment
        else -> null
    }
}

private const val URI_LIST_MIME_TYPE = "text/uri-list"

/** The content types the editor tells keyboards it accepts, so GIF and sticker keys insert files, not links. */
val keyboardContentMimeTypes: List<String> = mediaPrefixes.map { "$it*" }

/** Whether a keyboard's inserted file of [mimeType] can become part of an entry. */
fun acceptsKeyboardContent(mimeType: String): Boolean = mediaPrefixes.any(mimeType::startsWith)
