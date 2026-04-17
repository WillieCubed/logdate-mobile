package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import kotlin.uuid.Uuid

/**
 * Records that the user has opened a rewind.
 *
 * On the first call for a given rewind this sets the viewed flag and records
 * the timestamp. On subsequent calls it increments the view count.
 */
class MarkRewindViewedUseCase(
    private val rewindRepository: RewindRepository,
) {
    suspend operator fun invoke(rewindId: Uuid) {
        rewindRepository.markAsViewed(rewindId)
    }
}
