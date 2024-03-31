package app.logdate.model

sealed class UserPlace(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    /**
     * The plus code corresponding to the region where this place may be found.
     *
     * @see [Plus Codes docs](https://maps.google.com/pluscodes/)
     */
    val plusCode: String,
    val metadata: PlaceMetadata,
) {
    /**
     * A companion object that represents an unknown place.
     */
    companion object Unknown : UserPlace(
        uid = "unknown",
        latitude = 0.0,
        longitude = 0.0,
        altitude = 0.0,
        plusCode = "",
        metadata = PlaceMetadata(
            name = "Unknown",
            description = "Unknown place",
            address = "Unknown",
            city = "Unknown",
            country = "Unknown",
        ),
    )
}

data class PlaceMetadata(
    /**
     * The canonical name of the place.
     *
     * This is the name of the place, such as "Empire State Building" or "Eiffel Tower".
     */
    val name: String,
    val description: String,
    val address: String,
    val city: String,
    val country: String,
)