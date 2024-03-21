package app.logdate.core.world

import app.logdate.model.UserPlace

interface PlacesProvider {
    fun resolvePlace(latitude: Double, longitude: Double): List<UserPlace>
    fun getNearbyPlaces(place: UserPlace): List<UserPlace>
    fun getNearbyPlaces(latitude: Double, longitude: Double): List<UserPlace>
}
