package app.logdate.feature.rewind.domain

import app.logdate.core.data.rewind.RewindRepository
import app.logdate.model.Rewind
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * A use case that retrieves Rewinds that have already been generated.
 */
class GetPastRewindsUseCase @Inject constructor(
    private val rewindRepository: RewindRepository,
) {
    operator fun invoke(): Flow<List<Rewind>> {
        return rewindRepository.getAllRewinds()
    }
}