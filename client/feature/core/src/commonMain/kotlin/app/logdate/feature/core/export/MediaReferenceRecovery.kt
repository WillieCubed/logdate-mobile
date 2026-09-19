package app.logdate.feature.core.export

/**
 * How to find a media file whose stored reference no longer points at it.
 *
 * Older builds stored references that have since moved or been renamed: a doubled extension, a
 * recording that now lives in the app's audio folder, a media store id from before the file was
 * copied into private storage. These rules recognise those cases so the file can still be exported.
 * They only read names; opening a file is left to each platform.
 */
object MediaReferenceRecovery {
    enum class Collection { IMAGES, VIDEO, AUDIO }

    private val DOUBLED_EXTENSION = Regex("(\\.[A-Za-z0-9]+)\\1$")
    private val RECORDING_NAME = Regex("(recording_[A-Za-z0-9-]+(?:\\.[A-Za-z0-9]+)?)")
    private val TRAILING_DIGITS = Regex("(\\d{6,})$")

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "webp", "gif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "3gp", "webm")
    private val AUDIO_EXTENSIONS = setOf("m4a", "aac", "wav", "mp3", "ogg")

    /** `photo.jpg.jpg` becomes `photo.jpg`. */
    fun withoutDoubledExtension(name: String): String = name.replace(DOUBLED_EXTENSION) { it.groupValues[1] }

    /** The file name a recording was saved under in the app's audio folder, or null if [name] is not one. */
    fun recordingFileName(name: String): String? {
        val match = RECORDING_NAME.find(name) ?: return null
        val normalized = withoutDoubledExtension(match.value)
        return if ('.' in normalized) normalized else "$normalized.m4a"
    }

    /** The names an app-private copy of [fileName] may be stored under, best match first. */
    fun matchKeys(fileName: String): List<String> {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val trailingToken = fileName.substringAfterLast('_', "")
        val trailingStem = trailingToken.substringBeforeLast('.', trailingToken)
        return listOf(fileName, baseName, trailingToken, trailingStem).filter { it.isNotBlank() }.distinct()
    }

    fun matchesAny(
        candidateName: String,
        keys: List<String>,
    ): Boolean = keys.any { key -> candidateName == key || candidateName.startsWith("$key.") || candidateName.contains("_$key") }

    /** The media store id an old reference ends with, or null if it does not end in one. */
    fun legacyMediaStoreId(fileName: String): String? = TRAILING_DIGITS.find(fileName)?.groupValues?.get(1)

    fun collectionFor(extension: String?): Collection? {
        val lower = extension?.lowercase() ?: return null
        return when (lower) {
            in IMAGE_EXTENSIONS -> Collection.IMAGES
            in VIDEO_EXTENSIONS -> Collection.VIDEO
            in AUDIO_EXTENSIONS -> Collection.AUDIO
            else -> null
        }
    }
}
