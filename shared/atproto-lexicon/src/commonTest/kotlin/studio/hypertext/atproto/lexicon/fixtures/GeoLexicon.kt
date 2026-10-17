package studio.hypertext.atproto.lexicon.fixtures

import kotlinx.serialization.Serializable

public object GeoLexicon {
    public const val ID: String = "community.lexicon.location.geo"
}

/** A physical location in the form of a WGS84 coordinate. */
@Serializable
public data class Geo(
    val altitude: String? = null,
    val latitude: String,
    val longitude: String,
    /** The name of the location. */
    val name: String? = null,
)
