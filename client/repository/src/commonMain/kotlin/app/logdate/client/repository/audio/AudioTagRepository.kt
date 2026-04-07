package app.logdate.client.repository.audio

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * One ambient sound that the on-device tagger detected on an audio note.
 *
 * @param noteId the audio note this tag belongs to
 * @param soundName human-readable AudioSet label, e.g. "Bird", "Rain"
 * @param confidence model probability for this detection in [0, 1]
 * @param startMs offset from the start of the recording where the sound began
 * @param durationMs how long the sound was sustained
 */
data class AudioTag(
    val noteId: Uuid,
    val soundName: String,
    val confidence: Float,
    val startMs: Long,
    val durationMs: Long,
)

/**
 * Persistent store for ambient sound detections on audio notes.
 *
 * The tagger emits cumulative results as it scans a recording, so the common
 * write path is [replaceTagsForNote]: each emission completely replaces the
 * note's tag set with whatever the tagger has seen so far.
 */
interface AudioTagRepository {
    /**
     * Replaces all tags for [noteId] with the supplied [tags] in a single
     * transaction. Pass an empty list to clear the note's tags.
     */
    suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    )

    /** Returns the current tag set for [noteId], ordered by confidence desc. */
    suspend fun getTagsForNote(noteId: Uuid): List<AudioTag>

    /** Observes the tag set for [noteId] as it changes. */
    fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>>

    /**
     * Returns the ids of audio notes whose detected sounds include
     * [soundName] (case-insensitive). Used to power "find notes with birds"
     * style search.
     */
    suspend fun findNotesBySoundName(soundName: String): List<Uuid>
}
