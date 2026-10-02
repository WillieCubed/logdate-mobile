package app.logdate.wear.sync

import java.io.File

/**
 * Deletes the audio file of a note the phone removed, including audio pulled from the phone for
 * playback, which otherwise stays in storage for good.
 *
 * Only a file directly inside [audioDirectory] is touched, because a note's media reference can
 * also be a phone path or a content URI.
 *
 * @return true when a file was deleted.
 */
internal fun deleteLocalAudio(
    mediaRef: String?,
    audioDirectory: File,
): Boolean {
    val file = mediaRef?.takeIf { it.startsWith("/") }?.let(::File) ?: return false
    if (file.parentFile?.canonicalFile != audioDirectory.canonicalFile) return false
    return file.delete()
}
