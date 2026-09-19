package app.logdate.client.domain.export.archive

/**
 * Hands out archive paths that are unique on a case-insensitive file system.
 *
 * macOS and Windows treat `IMG.JPG` and `img.jpg` as one file, so two paths that differ only by case
 * would overwrite each other when the archive is extracted there. A repeated name gets a numeric
 * suffix before its extension, and a stem that is too long is shortened so the name stays within
 * [ArchivePath.MAX_SEGMENT_LENGTH].
 */
class ArchivePathAllocator {
    private val taken = mutableSetOf<String>()

    /** Marks a path with a fixed name as used so a generated name can never take it. */
    fun reserve(path: ArchivePath) {
        taken += path.value.lowercase()
    }

    fun allocate(
        directory: String,
        stem: String,
        extension: String,
    ): ArchivePath {
        require(stem.isNotEmpty()) { "A file needs a name" }
        var attempt = 1
        while (true) {
            val suffix = if (attempt == 1) "" else "_$attempt"
            val candidate = build(directory, stem, suffix, extension)
            if (taken.add(candidate.value.lowercase())) return candidate
            attempt++
        }
    }

    private fun build(
        directory: String,
        stem: String,
        suffix: String,
        extension: String,
    ): ArchivePath {
        val dotExtension = if (extension.isEmpty()) "" else ".$extension"
        val fileName = stem.take(ArchivePath.MAX_SEGMENT_LENGTH - suffix.length - dotExtension.length) + suffix + dotExtension
        return ArchivePath.of(if (directory.isEmpty()) fileName else "$directory/$fileName")
    }
}
