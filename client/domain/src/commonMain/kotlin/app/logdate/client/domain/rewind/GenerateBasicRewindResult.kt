package app.logdate.client.domain.rewind

import app.logdate.shared.model.Rewind

/**
 * Result of the rewind generation process.
 * 
 * This sealed interface represents all possible outcomes of attempting to generate
 * a rewind, allowing for proper handling of success, failure, and in-progress states
 * without relying on exceptions.
 */
sealed interface GenerateBasicRewindResult {
    /**
     * Successfully generated a new rewind.
     * 
     * @param rewind The generated rewind
     */
    data class Success(val rewind: Rewind) : GenerateBasicRewindResult
    
    /**
     * Generation is already in progress for the specified time period.
     * 
     * This result indicates that another process is already generating a rewind
     * for the same time period. Clients may want to wait and try again later.
     */
    data object AlreadyInProgress : GenerateBasicRewindResult
    
    /**
     * No content was found to generate a rewind.
     * 
     * This indicates that there were no notes or media items within the
     * specified time period to include in a rewind.
     */
    data object NoContent : GenerateBasicRewindResult
    
    /**
     * Generation failed due to an error.
     * 
     * @param error Description of what went wrong
     * @param exception The exception that caused the failure, if any
     */
    data class Error(
        val error: String,
        val exception: Throwable? = null
    ) : GenerateBasicRewindResult
}