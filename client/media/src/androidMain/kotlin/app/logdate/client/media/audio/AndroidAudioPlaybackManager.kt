package app.logdate.client.media.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AndroidAudioPlaybackManager(
    private val context: Context,
) : AudioPlaybackManager,
    AudioPlaybackStatusProvider {
    override val playbackStatus: StateFlow<AudioPlaybackStatus>
        get() = _playbackStatus

    private val _playbackStatus = MutableStateFlow(AudioPlaybackStatus())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor =
        Executor { runnable ->
            mainHandler.post(runnable)
        }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var onProgressUpdated: ((Float) -> Unit)? = null
    private var onPlaybackCompleted: (() -> Unit)? = null

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        this.onProgressUpdated = onProgressUpdated
        this.onPlaybackCompleted = onPlaybackCompleted
        // startService (not startForegroundService) ensures MediaSessionService.onStartCommand
        // fires so its MediaNotificationManager gets a valid startId. Without this, the service
        // is only bound via BIND_AUTO_CREATE and stops itself ~1s after the controller connects.
        context.startService(Intent(context, AudioPlaybackService::class.java))
        withController { player ->
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(uri)
                    .setMediaId(metadata?.noteId?.toString() ?: uri)
                    .setMediaMetadata(buildMediaMetadata(metadata))
                    .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            updateStatus(player)
        }
    }

    override fun pausePlayback() {
        withController { player ->
            player.pause()
            updateStatus(player)
        }
    }

    override fun stopPlayback() {
        withController { player ->
            player.stop()
            updateStatus(player, resetProgress = true)
        }
    }

    override fun seekTo(position: Float) {
        withController { player ->
            val duration = player.duration
            if (duration > 0) {
                player.seekTo((position * duration).toLong())
            }
        }
    }

    override fun release() {
        stopProgressTracking()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerFuture?.cancel(true)
        controllerFuture = null
    }

    private fun withController(onReady: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null) {
            onReady(existing)
            return
        }

        val future = controllerFuture ?: buildControllerFuture().also { controllerFuture = it }
        future.addListener({
            try {
                val resolved = future.get()
                if (controller == null) {
                    controller = resolved
                    resolved.addListener(playerListener)
                }
                onReady(resolved)
            } catch (e: Exception) {
                Napier.e(e) { "Failed to create MediaController for audio playback" }
                onPlaybackCompleted?.invoke()
            }
        }, mainExecutor)
    }

    private fun buildControllerFuture(): ListenableFuture<MediaController> {
        val token =
            SessionToken(
                context,
                ComponentName(context, AudioPlaybackService::class.java),
            )
        return MediaController.Builder(context, token).buildAsync()
    }

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
                controller?.let { updateStatus(it) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = controller ?: return
                if (playbackState == Player.STATE_ENDED) {
                    onProgressUpdated?.invoke(1f)
                    onPlaybackCompleted?.invoke()
                    updateStatus(player, completed = true)
                    stopProgressTracking()
                    return
                }
                updateStatus(player)
            }
        }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob =
            scope.launch {
                while (isActive) {
                    controller?.let { player ->
                        val durationMs = player.duration
                        if (durationMs > 0) {
                            val progress = player.currentPosition.toFloat() / durationMs.toFloat()
                            val normalized = progress.coerceIn(0f, 1f)
                            onProgressUpdated?.invoke(normalized)
                            _playbackStatus.value = _playbackStatus.value.copy(progress = normalized)
                        }
                    }
                    delay(100)
                }
            }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateStatus(
        player: MediaController,
        resetProgress: Boolean = false,
        completed: Boolean = false,
    ) {
        val durationMs = player.duration
        val duration = if (durationMs > 0) durationMs.milliseconds else Duration.ZERO
        val progress =
            when {
                resetProgress -> 0f
                completed -> 1f
                durationMs > 0 -> (player.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }
        _playbackStatus.value =
            AudioPlaybackStatus(
                isPlaying = player.isPlaying,
                progress = progress,
                duration = duration,
            )
    }

    /**
     * Builds media metadata for system UI surfaces and deep-link routing.
     */
    private fun buildMediaMetadata(metadata: AudioPlaybackMetadata?): MediaMetadata {
        val extras = Bundle()
        metadata?.noteId?.let { extras.putString(EXTRA_NOTE_ID, it.toString()) }
        return MediaMetadata
            .Builder()
            .setTitle(metadata?.title ?: "Voice Note")
            .setSubtitle(metadata?.subtitle)
            .setExtras(extras.takeIf { !it.isEmpty })
            .build()
    }
}
