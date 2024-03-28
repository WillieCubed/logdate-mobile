package app.logdate.core.world

import app.logdate.model.UserPlace

data class UserPlaceResult(
    val place: UserPlace,
    val confidence: Int
)