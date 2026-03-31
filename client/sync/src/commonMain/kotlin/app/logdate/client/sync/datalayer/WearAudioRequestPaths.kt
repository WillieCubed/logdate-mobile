package app.logdate.client.sync.datalayer

import kotlin.uuid.Uuid

/**
 * Shared Wear Data Layer paths for phone-originated note sync and on-demand audio transfer.
 */
object WearAudioRequestPaths {
    const val SYNC_REQUEST_PATH = "/logdate/sync/request"

    private const val NOTES_PATH_PREFIX = "/logdate/notes"
    private const val AUDIO_SEGMENT = "/audio"
    private const val REQUEST_SUFFIX = "$AUDIO_SEGMENT/request"

    fun audioTransferPath(noteId: Uuid): String = "$NOTES_PATH_PREFIX/$noteId$AUDIO_SEGMENT"

    fun audioRequestPath(noteId: Uuid): String = "$NOTES_PATH_PREFIX/$noteId$REQUEST_SUFFIX"

    fun isAudioRequestPath(path: String): Boolean =
        path.startsWith("$NOTES_PATH_PREFIX/") &&
            path.endsWith(REQUEST_SUFFIX)

    fun noteIdFromAudioRequestPath(path: String): Uuid {
        require(isAudioRequestPath(path)) { "Invalid audio request path: $path" }
        val segment = path.removePrefix("$NOTES_PATH_PREFIX/").removeSuffix(REQUEST_SUFFIX)
        return Uuid.parse(segment)
    }
}
