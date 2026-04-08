package app.logdate.client.database.dao.rewind

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.logdate.client.database.entities.rewind.ReflectionPromptResponseEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Storage for the user's typed replies to noticing prompts inside a rewind.
 *
 * Replies are keyed `(rewindId, promptKey)` — see [ReflectionPromptResponseEntity] —
 * so [upsert] cleanly replaces an existing reply when the user edits it. The flow
 * returned by [observeForRewind] emits whenever any reply on the rewind is written or
 * deleted; the rewind detail screen consumes it to overlay existing responses on the
 * matching prompt panels.
 */
@Dao
interface ReflectionPromptResponseDao {
    @Upsert
    suspend fun upsert(response: ReflectionPromptResponseEntity)

    @Query("SELECT * FROM rewind_prompt_responses WHERE rewindId = :rewindId")
    fun observeForRewind(rewindId: Uuid): Flow<List<ReflectionPromptResponseEntity>>

    @Query("SELECT * FROM rewind_prompt_responses WHERE rewindId = :rewindId AND promptKey = :promptKey LIMIT 1")
    suspend fun get(
        rewindId: Uuid,
        promptKey: String,
    ): ReflectionPromptResponseEntity?

    @Query("DELETE FROM rewind_prompt_responses WHERE rewindId = :rewindId AND promptKey = :promptKey")
    suspend fun delete(
        rewindId: Uuid,
        promptKey: String,
    )
}
