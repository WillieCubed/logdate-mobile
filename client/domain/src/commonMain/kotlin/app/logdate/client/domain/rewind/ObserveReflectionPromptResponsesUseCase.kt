package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.ReflectionPromptResponseRepository
import app.logdate.shared.model.ReflectionPromptKey
import app.logdate.shared.model.ReflectionPromptResponse
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Streams the user's typed replies for a single rewind, keyed by prompt.
 *
 * The map is small (at most one entry per noticing prompt the rewind contains), so the
 * detail screen joins it against its panel list cheaply on every emission.
 */
class ObserveReflectionPromptResponsesUseCase(
    private val repository: ReflectionPromptResponseRepository,
) {
    operator fun invoke(rewindId: Uuid): Flow<Map<ReflectionPromptKey, ReflectionPromptResponse>> = repository.observe(rewindId)
}
