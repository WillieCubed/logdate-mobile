package app.logdate.client.media.audio

/**
 * Resolves the duration of an audio file from its URI or file path.
 */
interface AudioDurationResolver {
    /**
     * @return Duration in milliseconds if available, otherwise null.
     */
    suspend fun resolveDurationMs(uri: String): Long?
}
