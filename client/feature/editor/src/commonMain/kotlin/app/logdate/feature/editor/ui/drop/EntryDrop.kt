package app.logdate.feature.editor.ui.drop

/** One item in a drag from another app: a file reference, some text, or both. */
data class DroppedItem(
    val mimeType: String,
    val uri: String?,
    val text: String?,
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
 */
fun mediaPasteItem(clipMimeTypes: List<String>): MediaPasteItem? {
    if (clipMimeTypes.none { mime -> mediaPrefixes.any(mime::startsWith) }) return null
    return if (clipMimeTypes.any { it.startsWith("text/") }) MediaPasteItem.PasteAsAttachment else MediaPasteItem.Paste
}

/** The content types the editor tells keyboards it accepts, so GIF and sticker keys insert files, not links. */
val keyboardContentMimeTypes: List<String> = mediaPrefixes.map { "$it*" }

/** Whether a keyboard's inserted file of [mimeType] can become part of an entry. */
fun acceptsKeyboardContent(mimeType: String): Boolean = mediaPrefixes.any(mimeType::startsWith)
