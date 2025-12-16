package app.logdate.client.repository.rewind

import app.logdate.shared.model.Rewind
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * A repository for managing Rewind data.
 */
interface RewindRepository {
    /**
     * Retrieves all Rewinds that have been generated.
     */
    fun getAllRewinds(): Flow<List<Rewind>>

    /**
     * Retrieves a Rewind by its unique identifier.
     */
    fun getRewind(uid: Uuid): Flow<Rewind>

    /**
     * Retrieves a Rewind for a given time period.
     *
     * @param start The start of the time period.
     * @param end The end of the time period.
     * @return The Rewind for the given time period.
     */
    fun getRewindBetween(start: Instant, end: Instant): Flow<Rewind?>

    /**
     * Retrieves a rewind using data between the given date to the current instant.
     */
    fun getRewindAfter(date: Instant): Flow<Rewind?> = getRewindBetween(date, Clock.System.now())

    /**
     * Checks if a Rewind is available for the given time period.
     *
     * If there is no content available for the given time period, the Rewind is not available.
     */
    suspend fun isRewindAvailable(start: Instant, end: Instant): Boolean

    /**
     * Creates a new Rewind for the given time period.
     *
     * This will use all available user data to create the Rewind, including:
     * - Journal entry text content
     * - Location history
     *
     * @param start The start of the time period.
     * @param end The end of the time period.
     * @return The created Rewind.
     * @deprecated Use GenerateBasicRewindUseCase instead
     */
    @Deprecated("Use GenerateBasicRewindUseCase instead")
    suspend fun createRewind(start: Instant, end: Instant): Rewind
    
    /**
     * Saves a rewind to the repository.
     * 
     * @param rewind The rewind to save
     */
    suspend fun saveRewind(rewind: Rewind)
}