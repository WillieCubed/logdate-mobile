package app.logdate.client.sync.continuity

interface WatchRecordingCompanion {
    suspend fun current(): WatchLiveSessionStore.Session?

    suspend fun stop(): Boolean
}
