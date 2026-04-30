@file:OptIn(ExperimentalSerializationApi::class)

package app.logdate.client.repository.journals

import app.logdate.util.UuidSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A repository for managing entry drafts.
 *
 */
interface EntryDraftRepository {
    /**
     * Returns a flow containing all of the current user's entry drafts.
     */
    fun getDrafts(): Flow<List<EntryDraft>>

    /**
     * Retrieves the draft with the given ID.
     *
     * @return A result containing the entry draft, otherwise a failure result if no draft with the given ID exists.
     */
    fun getDraft(uid: Uuid): Flow<Result<EntryDraft>>

    /**
     * Creates a new draft with the given content.
     *
     * @return The UID of the draft
     */
    suspend fun createDraft(notes: List<JournalNote>): Uuid

    /**
     * Updates a draft with the given notes and returns its ID.
     */
    suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
    ): Uuid

    /**
     * Replaces the [EntryDraft.pendingMedia] list of the draft with [uid].
     *
     * This is the durable record of in-flight media (e.g., audio recordings that
     * have started but not yet finalized into a [JournalNote]) so the editor can
     * survive process death and reattach the recording to its draft on relaunch.
     *
     * No-op when no draft with [uid] exists.
     */
    suspend fun setPendingMedia(
        uid: Uuid,
        pendingMedia: List<PendingMediaRecord>,
    )

    /**
     * Deletes any drafts with the given UID.
     *
     * If no draft with the given UID exists, this is a no-op.
     */
    suspend fun deleteDraft(uid: Uuid)

    /**
     * Deletes all drafts.
     */
    suspend fun deleteAllDrafts()

    /**
     * Deletes drafts that have not been updated within [maxAge].
     *
     * @return The number of drafts deleted.
     */
    suspend fun deleteExpiredDrafts(maxAge: Duration): Int
}

/**
 * An entry draft consists of a series of journal notes produced in a single editing session.
 *
 * [pendingMedia] tracks in-flight media (e.g., audio recordings whose URI has not
 * yet been resolved) so the editor can recover the recording on relaunch. The
 * field defaults to an empty list, which keeps deserialization backward-compatible
 * with drafts written by older app versions.
 */
@Serializable
data class EntryDraft(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val notes: List<JournalNote>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pendingMedia: List<PendingMediaRecord> = emptyList(),
)

/** Type of pending media. Currently only audio is wired; camera/video will follow. */
@Serializable
enum class PendingMediaType {
    AUDIO,
}

/**
 * A record of in-flight media owned by an [EntryDraft].
 *
 * Persisted alongside the draft so a recording started in one process can be
 * recovered by a future process — the draft is the registry, no separate
 * orphan-tracking is needed (per design: "all notes should already be associated
 * with entry drafts").
 *
 * @property filePath Absolute path on the recording device, when known. May be
 *   null for transient states where the path has not yet been resolved by the
 *   recording side.
 */
@Serializable
data class PendingMediaRecord(
    @Serializable(with = UuidSerializer::class)
    val blockId: Uuid,
    val mediaType: PendingMediaType,
    val createdAt: Instant,
    val filePath: String? = null,
)
