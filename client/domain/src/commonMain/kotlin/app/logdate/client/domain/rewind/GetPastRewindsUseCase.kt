package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.flow.Flow

/**
 * A use case that retrieves Rewinds that have already been generated.
 */
class GetPastRewindsUseCase(
    private val rewindRepository: RewindRepository,
) {
    operator fun invoke(): Flow<List<Rewind>> {
        return rewindRepository.getAllRewinds()
    }
}