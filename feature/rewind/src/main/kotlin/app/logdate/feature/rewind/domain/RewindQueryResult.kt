package app.logdate.feature.rewind.domain

import app.logdate.model.Rewind

/**
 * A domain-layer result for a Rewind query.
 */
sealed class RewindQueryResult {
    /**
     * Indicates that the [Rewind] was successfully retrieved.
     */
    data class Success(val rewind: Rewind) : RewindQueryResult()

    /**
     * Indicates that the user is not allowed to view a [Rewind] at this time.
     *
     * This will be the case if the user is attempting to view a Rewind before it is ready, like if
     * the user is trying to view a Rewind for a time period that has not yet ended.
     */
    object NotReady : RewindQueryResult()

    /**
     * Indicates that there are no [Rewind]s available for the given time period.
     *
     * This may be because the user doest not have any activity for the given time period, like if
     * a Rewind was requested for a time before the user was using the app.
     */
    object NoneAvailable : RewindQueryResult()
}