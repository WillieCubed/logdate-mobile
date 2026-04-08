package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import kotlin.uuid.Uuid

/**
 * Removes a rewind and everything attached to it.
 *
 * The repository's underlying foreign keys cascade-delete the rewind's text, image,
 * video, and prompt-reply rows so callers don't need to think about cleanup. The use
 * case exists so the view model has a single seam to call without reaching past the
 * domain layer.
 */
class DeleteRewindUseCase(
    private val rewindRepository: RewindRepository,
) {
    suspend operator fun invoke(rewindId: Uuid) {
        rewindRepository.deleteRewind(rewindId)
    }
}
