package app.logdate.feature.editor.ui.video

import io.github.aakira.napier.Napier
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

/**
 * Single shared factory for the Desktop video player. Creating a
 * [MediaPlayerFactory] is non-trivial (it spins up libVLC) so the whole app
 * shares one instance and acquires fresh player objects from it as needed.
 *
 * Stays an `object` rather than a Koin singleton because VLCJ's native side
 * doesn't survive multiple factory teardown / restart cycles within a JVM.
 */
internal object VlcMediaPlayerFactory {
    @Volatile
    private var sharedFactory: MediaPlayerFactory? = null

    /**
     * Returns true when libVLC is available on the host. Some environments
     * (notably CI runners without libVLC installed) will throw on factory
     * construction — the caller treats that as "fall back to the external
     * system player" rather than crashing.
     */
    fun isAvailable(): Boolean = ensureFactory() != null

    fun newEmbeddedPlayer(): EmbeddedMediaPlayer? = ensureFactory()?.mediaPlayers()?.newEmbeddedMediaPlayer()

    @Synchronized
    private fun ensureFactory(): MediaPlayerFactory? {
        val existing = sharedFactory
        if (existing != null) return existing
        return runCatching { MediaPlayerFactory() }
            .onFailure { error ->
                Napier.w("VLCJ MediaPlayerFactory unavailable — falling back to system player", error)
            }.getOrNull()
            ?.also { sharedFactory = it }
    }
}
