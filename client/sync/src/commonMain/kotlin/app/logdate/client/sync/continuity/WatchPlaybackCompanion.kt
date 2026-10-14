package app.logdate.client.sync.continuity

interface WatchPlaybackCompanion {
    val lastFailure: HandoffFailure? get() = null

    suspend fun available(): Boolean

    suspend fun continueOnWatch(): Boolean
}
