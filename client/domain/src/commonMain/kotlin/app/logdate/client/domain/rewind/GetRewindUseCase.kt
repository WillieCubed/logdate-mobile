package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * A use case that retrieves or constructs a Rewind for a given time period.
 */
class GetRewindUseCase(
    private val rewindRepository: RewindRepository,
) {
    /**
     * Retrieves a Rewind for a given time period.
     *
     * @param params Information used to retrieve the Rewind.
     */
    operator fun invoke(params: RewindParams): Flow<RewindQueryResult> {
        val result = rewindRepository.getRewindBetween(params.start, params.end)
            .map { rewind ->
                if (rewind != null) {
                    RewindQueryResult.Success(rewind)
                } else {
                    RewindQueryResult.NotReady
                }
            }
        return result
    }
}

/**
 * Request parameters for fetching a [Rewind].
 */
data class RewindParams(
    /***
     * The start of the time period for the Rewind.
     */
    val start: Instant,
    /**
     * The end of the time period for the Rewind.
     */
    val end: Instant,
)

