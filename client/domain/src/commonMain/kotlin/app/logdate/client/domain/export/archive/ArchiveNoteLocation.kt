package app.logdate.client.domain.export.archive

import kotlinx.serialization.Serializable

/** Source coordinates and semantic identity are independent; neither implies the other was recorded. */
@Serializable
data class ArchiveNoteLocation(
    val coordinates: ArchiveNoteCoordinates? = null,
    val place: ArchivePlaceReference? = null,
)

@Serializable
data class ArchiveNoteCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
)

@Serializable
data class ArchivePlaceReference(
    val id: String,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require((latitude == null) == (longitude == null)) { "A place center requires both coordinates" }
    }
}
