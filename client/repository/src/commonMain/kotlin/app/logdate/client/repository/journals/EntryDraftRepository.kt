@file:OptIn(ExperimentalSerializationApi::class)

package app.logdate.client.repository.journals

import app.logdate.util.UuidSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
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
    suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid

    /**
     * Deletes any drafts with the given UID.
     *
     * If no draft with the given UID exists, this is a no-op.
     */
    suspend fun deleteDraft(uid: Uuid)
}

/**
 * An entry draft consists of a series of journal notes produced in a single editing session.
 */
@Serializable
data class EntryDraft(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val notes: List<JournalNote>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
