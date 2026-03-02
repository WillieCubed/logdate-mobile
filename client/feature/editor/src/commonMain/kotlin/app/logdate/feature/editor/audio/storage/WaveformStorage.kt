package app.logdate.feature.editor.audio.storage

/**
 * Interface for persisting waveform amplitude data.
 *
 * Waveform data is stored as binary packed floats in the cache directory,
 * not in the database, to avoid bloating the database with large BLOB data.
 * Typical size: ~1.2KB per recording (300 floats * 4 bytes).
 */
interface WaveformStorage {
    /**
     * Saves waveform amplitudes for an audio file.
     *
     * @param audioUri The URI of the audio file (used as key)
     * @param amplitudes Normalized amplitude values (0.0 to 1.0)
     */
    suspend fun save(
        audioUri: String,
        amplitudes: List<Float>,
    )

    /**
     * Loads waveform amplitudes for an audio file.
     *
     * @param audioUri The URI of the audio file
     * @return The amplitudes if found, null otherwise
     */
    suspend fun load(audioUri: String): List<Float>?

    /**
     * Checks if waveform data exists for an audio file.
     */
    suspend fun exists(audioUri: String): Boolean

    /**
     * Deletes waveform data for an audio file.
     */
    suspend fun delete(audioUri: String)
}
