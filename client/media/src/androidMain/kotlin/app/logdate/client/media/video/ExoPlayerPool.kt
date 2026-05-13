package app.logdate.client.media.video

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.aakira.napier.Napier
import java.util.ArrayDeque
import java.util.Deque

/**
 * Process-singleton pool of [ExoPlayer] instances shared across every video
 * surface in the app. Rebuilding an ExoPlayer is expensive — it allocates
 * renderer threads, sets up codec instances, and negotiates audio focus — and
 * users notice that work as a brief stall on scroll. The pool keeps up to
 * [MAX_PLAYERS] warm so a carousel or vertical Rewind feed can hand off
 * players between visible items without churn.
 *
 * Lifecycle contract: composables call [acquire] when entering composition,
 * [release] when leaving. Released players are paused, drained of any media
 * items, and parked back in the pool for the next caller. The underlying
 * [ExoPlayer] only gets [ExoPlayer.release] when the pool overflows or the
 * app shuts down.
 *
 * Players are configured with the shared [MediaCache], so a video the user
 * already scrubbed once replays from disk on a second viewing.
 */
@UnstableApi
class ExoPlayerPool(
    private val context: Context,
    private val mediaCache: MediaCache,
) {
    private val available: Deque<ExoPlayer> = ArrayDeque(MAX_PLAYERS)
    private var liveCount: Int = 0

    /**
     * Returns an [ExoPlayer] ready for [ExoPlayer.setMediaItem] + [ExoPlayer.prepare].
     * Callers must pair each `acquire` with a [release] when the surface leaves
     * composition.
     */
    @Synchronized
    fun acquire(): ExoPlayer {
        available.pollFirst()?.let { return it }
        if (liveCount < MAX_PLAYERS) {
            liveCount += 1
            return buildPlayer()
        }
        // Soft cap: if more than MAX_PLAYERS surfaces try to play simultaneously
        // — rare in practice — we hand out a fresh player rather than block.
        // The caller still owes us a release; on release we'll discard the
        // surplus instance.
        return buildPlayer()
    }

    /**
     * Returns [player] to the pool for reuse. The player is paused and its
     * media items cleared so the next caller starts from a clean slate. If
     * the pool is already full, the surplus player is fully released.
     */
    @Synchronized
    fun release(player: ExoPlayer) {
        try {
            player.pause()
            player.clearMediaItems()
        } catch (e: Throwable) {
            // Defensive — never let teardown crash the host.
            Napier.w("Failed to park ExoPlayer", e)
        }
        if (available.size < MAX_PLAYERS) {
            available.addFirst(player)
        } else {
            try {
                player.release()
            } finally {
                liveCount = (liveCount - 1).coerceAtLeast(0)
            }
        }
    }

    /**
     * Releases every parked player and resets the pool. Not called in normal
     * app lifecycle — the pool is process-lifetime — but exposed for tests
     * and for future process-shutdown hooks.
     */
    @Synchronized
    fun shutdown() {
        for (player in available) {
            runCatching { player.release() }
        }
        available.clear()
        liveCount = 0
    }

    private fun buildPlayer(): ExoPlayer {
        val sourceFactory = DefaultMediaSourceFactory(mediaCache.dataSourceFactory(context))
        return ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(sourceFactory)
            .build()
    }

    companion object {
        // Two instances is enough for a vertical Rewind feed (current + next).
        // Three covers a horizontal carousel without thrash.
        private const val MAX_PLAYERS = 3
    }
}
