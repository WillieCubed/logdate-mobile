package app.logdate

/** LogDate's registered Wear OS tracks use the same aliases as its phone tracks. */
object WearPlayTrack {
    fun forTrack(track: String): String = "wear:$track"
}
