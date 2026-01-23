package app.logdate.feature.editor.audio.storage

/**
 * iOS implementation of WaveformStorage.
 *
 * TODO: Implement using NSFileManager and Caches directory
 */
class IosWaveformStorage : WaveformStorage {
    override suspend fun save(audioUri: String, amplitudes: List<Float>) {
        // TODO: Implement using Foundation/NSFileManager
    }

    override suspend fun load(audioUri: String): List<Float>? {
        // TODO: Implement
        return null
    }

    override suspend fun exists(audioUri: String): Boolean {
        // TODO: Implement
        return false
    }

    override suspend fun delete(audioUri: String) {
        // TODO: Implement
    }
}
