package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.ReflectionPromptResponseRepository
import app.logdate.shared.model.ReflectionPrompt
import kotlin.uuid.Uuid

/**
 * Persists what the user typed back at one of the rewind's noticing prompts.
 *
 * Saving an empty string is treated as "clear my reply" — the use case routes blank
 * input to delete so the view model doesn't have to know whether the user is editing
 * for real or wiping their answer. The trim is intentional: a reply that is purely
 * whitespace is the same as no reply at all.
 */
class SaveReflectionPromptResponseUseCase(
    private val repository: ReflectionPromptResponseRepository,
) {
    suspend operator fun invoke(
        rewindId: Uuid,
        prompt: ReflectionPrompt,
        responseText: String,
    ) {
        val trimmed = responseText.trim()
        if (trimmed.isEmpty()) {
            repository.delete(rewindId, prompt)
        } else {
            repository.save(rewindId, prompt, trimmed)
        }
    }
}
