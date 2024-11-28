package app.logdate.client.location.places

import app.logdate.shared.model.UserPlace


data class UserPlaceResult(
    val place: UserPlace,
    val confidence: Int
)