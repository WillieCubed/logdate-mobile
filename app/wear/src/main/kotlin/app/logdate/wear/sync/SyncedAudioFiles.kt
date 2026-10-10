package app.logdate.wear.sync

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaFileResolver
import java.io.File

/**
 * Deletes the audio file of a note the phone removed, including audio pulled from the phone for
 * playback, which otherwise stays in storage for good.
 *
 * Only a file directly inside the watch's recordings directory is touched, because a note's media
 * reference can also be a phone path or a content URI.
 *
 * @return true when a file was deleted.
 */
internal fun deleteLocalAudio(
    mediaRef: String?,
    mediaFiles: MediaFileResolver,
): Boolean {
    val file = mediaRef?.let(mediaFiles::filePath)?.let(::File) ?: return false
    val recordings = File(mediaFiles.directory(MediaCollection.Recordings)).canonicalFile
    if (file.parentFile?.canonicalFile != recordings) return false
    return file.delete()
}
