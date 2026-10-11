package app.logdate.wear.playback

import app.logdate.wear.sync.SyncedAudioCache
import java.io.File
import java.io.IOException
import kotlin.uuid.Uuid

/**
 * Audio the watch stores for notes it did not record, so they play without the phone.
 *
 * Today audio arrives from the phone when someone plays a note. Prefetching the library window on the
 * charger and streaming by byte range store through this same boundary.
 */
interface WatchMediaCache {
    /** The complete audio stored for [noteId], or null when there is none. */
    fun cachedFile(noteId: Uuid): File?

    /**
     * Stores [noteId]'s audio, written by [download] to the path it is given.
     *
     * @return the complete file, or null when [download] failed.
     * @throws NoRoomForAudioException when the watch is short of space.
     */
    suspend fun store(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File?

    /** Removes [noteId]'s stored audio. @return true when a file was deleted. */
    fun evict(noteId: Uuid): Boolean
}

/** The watch has less free space than audio may use without crowding out new recordings. */
class NoRoomForAudioException(
    freeBytes: Long,
    reserveBytes: Long,
) : IOException("$freeBytes bytes free; audio is stored only above $reserveBytes")

/** A note's audio is on the phone, and no phone is in reach to fetch it from. */
class AudioNotOnWatchException(
    noteId: Uuid,
) : IOException("The audio of note $noteId is on the phone, and no phone is in reach")

/**
 * [WatchMediaCache] in [SyncedAudioCache]'s directory. Audio is stored only while more than
 * [reserveBytes] are free, so playback never takes the space a new recording needs.
 */
class SyncedAudioWatchMediaCache(
    private val cache: SyncedAudioCache,
    private val freeBytes: suspend () -> Long,
    private val reserveBytes: Long = DEFAULT_RESERVE_BYTES,
) : WatchMediaCache {
    override fun cachedFile(noteId: Uuid): File? = cache.cachedFile(noteId)

    override suspend fun store(
        noteId: Uuid,
        extension: String,
        download: suspend (partPath: String) -> Boolean,
    ): File? {
        val free = freeBytes()
        if (free < reserveBytes) throw NoRoomForAudioException(free, reserveBytes)
        return cache.pull(noteId, extension, download)
    }

    override fun evict(noteId: Uuid): Boolean = cache.delete(noteId)

    companion object {
        const val DEFAULT_RESERVE_BYTES = 200L * 1024 * 1024
    }
}
