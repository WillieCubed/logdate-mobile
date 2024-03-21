package app.logdate.model

data class UserPlace(
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
)

data class PlaceMetadata(
    /**
     * The canonical name of the place.
     *
     * This is the name of the place, such as "Empire State Building" or "Eiffel Tower".
     */
    val name: String,
    val description: String,
    val location: UserPlace,
    val address: String,
    val city: String,
    val country: String,
)