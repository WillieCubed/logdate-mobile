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
