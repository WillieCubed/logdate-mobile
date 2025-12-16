package app.logdate.client.location.places

import app.logdate.shared.model.Place

/**
 * Represents the result of a place detection or lookup operation.
 * 
 * Contains the place information along with a confidence score indicating
 * how certain the system is about this result.
 */
data class UserPlaceResult(
    val place: Place.UserDefined,
    val confidence: Int
)