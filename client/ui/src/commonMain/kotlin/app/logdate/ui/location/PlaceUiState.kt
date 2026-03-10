package app.logdate.ui.location

data class PlaceUiState(
    val id: String,
    val title: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
