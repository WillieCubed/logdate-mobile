package app.logdate.client.repository.rewind

import app.logdate.shared.model.ReflectionPrompt
import app.logdate.shared.model.ReflectionPromptKey
import app.logdate.shared.model.ReflectionPromptResponse
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Storage for the user's typed replies to noticing prompts in a rewind.
 *
 * Each rewind has at most one reply per prompt; saving twice on the same prompt edits
 * the existing reply in place. The map returned by [observe] is keyed by the prompt's
 * stable [ReflectionPromptKey], so the rewind detail screen can join responses to
 * panels regardless of how many other prompts the rewind contains.
 */
interface ReflectionPromptResponseRepository {
    /** Emits the current set of replies for [rewindId], keyed by prompt. */
    fun observe(rewindId: Uuid): Flow<Map<ReflectionPromptKey, ReflectionPromptResponse>>

    /**
     * Persists a reply to [prompt] inside [rewindId].
     *
     * @param responseText the user's typed reply, exactly as entered. Must be non-blank;
     *   the use case layer is responsible for routing blank submissions to [delete].
     */
    suspend fun save(
        rewindId: Uuid,
        prompt: ReflectionPrompt,
        responseText: String,
    )

    /** Removes the user's reply to [prompt] inside [rewindId], if any. */
    suspend fun delete(
        rewindId: Uuid,
        prompt: ReflectionPrompt,
    )
}
