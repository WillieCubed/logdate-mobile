package app.logdate.feature.editor.ui.editor

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "heics", "heifs", "avif", "bmp")

private val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp", "3g2", "avi", "mpg", "mpeg", "ogv", "wmv")

private val audioExtensions = setOf("m4a", "mp3", "ogg", "oga", "aac", "amr", "flac", "mid", "midi", "opus", "wav", "weba")

/**
 * The kind of block an attachment at [uri] opens as. LogDate stores media under its type's file
 * extension, so the extension decides; a content URI with none falls back to the media type in its
 * path (`.../video/...`), and anything else opens as a photo.
 */
internal fun attachmentBlockType(uri: String): BlockType {
    val extension =
        uri
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
    return when {
        extension in imageExtensions -> BlockType.IMAGE
        extension in videoExtensions -> BlockType.VIDEO
        extension in audioExtensions -> BlockType.AUDIO
        uri.contains("video/", ignoreCase = true) -> BlockType.VIDEO
        uri.contains("audio/", ignoreCase = true) -> BlockType.AUDIO
        else -> BlockType.IMAGE
    }
}
