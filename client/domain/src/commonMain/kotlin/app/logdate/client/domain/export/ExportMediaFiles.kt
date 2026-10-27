package app.logdate.client.domain.export

import io.github.aakira.napier.Napier
import kotlin.time.Instant

private val EXTENSION_PATTERN = Regex("\\.[a-zA-Z0-9]+$")

private fun createMediaPath(
    uri: String,
    seed: String,
    timestamp: Instant,
    noteType: String? = null,
): String {
    val rawFileName =
        uri
            .substringAfterLast("/")
            .substringBefore("?")

    val extension =
        EXTENSION_PATTERN
            .find(rawFileName)
            ?.value
            ?.substring(1)
            ?.lowercase()
            ?: run {
                // Content URIs (e.g. content://media/external/images/media/1000025292)
                // carry no extension in the last path segment. Infer from the URI
                // path structure first, then fall back to the note type.
                val inferred = inferExtensionFromUri(uri, noteType)
                Napier.w(
                    "No extension found in URI: $uri — " +
                        if (inferred != null) {
                            "using .$inferred inferred from content type"
                        } else {
                            "falling back to .bin; MIME type will be detected on import"
                        },
                )
                inferred ?: "bin"
            }

    return "${ExportFileStructure.MEDIA_FOLDER}/$seed.$extension"
}

/**
 * Infers a file extension for URIs that carry no extension in their path segment.
 *
 * Bare MediaStore content URIs (e.g. `content://media/external/images/media/123`) have no
 * extension in the last path segment. The URI collection name (/images/, /video/, /audio/)
 * is checked first because it is more reliable than [noteType], which comes from the
 * serialized export model and could be stale.
 */
private fun inferExtensionFromUri(
    uri: String,
    noteType: String?,
): String? {
    val lower = uri.lowercase()
    return when {
        "/images/" in lower -> "jpg"
        "/video/" in lower -> "mp4"
        "/audio/" in lower -> "m4a"
        noteType == "image" -> "jpg"
        noteType == "video" -> "mp4"
        noteType == "audio" -> "m4a"
        else -> null
    }
}

internal fun getMediaFilesToExport(
    notes: List<ExportNote>,
    drafts: List<ExportDraft>,
): List<ExportMediaFile> {
    val mediaFilesBySource = linkedMapOf<String, ExportMediaFile>()

    notes.forEach { note ->
        val mediaPath = note.mediaPath ?: return@forEach
        mediaFilesBySource.getOrPut(mediaPath) {
            ExportMediaFile(
                exportPath = createMediaPath(mediaPath, note.id, note.createdAt, note.type),
                sourceUri = mediaPath,
            )
        }
    }

    drafts.forEach { draft ->
        draft.mediaReferences.forEachIndexed { index, mediaRef ->
            mediaFilesBySource.getOrPut(mediaRef) {
                ExportMediaFile(
                    exportPath = createMediaPath(mediaRef, "${draft.id}_$index", draft.createdAt),
                    sourceUri = mediaRef,
                )
            }
        }
    }

    return mediaFilesBySource.values.toList()
}
