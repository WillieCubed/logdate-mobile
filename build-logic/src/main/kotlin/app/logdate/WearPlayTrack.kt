package app.logdate

/**
 * Google Play's name for a track of the Wear OS form factor. A form factor's tracks are `wear:` plus
 * the track's default name, and the default name of Internal testing is `qa`, so the phone's `internal`
 * is the watch's `wear:qa`. Beta, production and closed testing tracks keep their own names.
 */
object WearPlayTrack {
    fun forTrack(track: String): String = "wear:" + if (track == "internal") "qa" else track
}
