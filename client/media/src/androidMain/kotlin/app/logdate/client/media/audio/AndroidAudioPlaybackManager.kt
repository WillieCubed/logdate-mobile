package app.logdate.client.media.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.androidMediaFileResolver
import app.logdate.client.media.storage.playableUri
import com.google.common.util.concurrent.ListenableFuture
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface MediaControllerFutureFactory {
    fun create(context: Context): ListenableFuture<MediaController>
}

fun interface AudioPlaybackItemFactory {
    fun create(
        uri: String,
        metadata: AudioPlaybackMetadata?,
    ): MediaItem
}

fun interface AudioPlaybackServiceStarter {
    fun start(context: Context)
}

class AndroidAudioPlaybackManager(
    private val context: Context,
    coroutineScope: CoroutineScope,
    progressDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val controllerFactory: MediaControllerFutureFactory = defaultMediaControllerFutureFactory,
    private val controllerExecutor: Executor = mainThreadExecutor(),
    private val mediaItemFactory: AudioPlaybackItemFactory = defaultMediaItemFactory,
    private val serviceStarter: AudioPlaybackServiceStarter = defaultAudioPlaybackServiceStarter,
    private val mediaFiles: MediaFileResolver = androidMediaFileResolver(context),
    private val skipSilenceCommand: SkipSilenceCommand = SkipSilenceCommand.Session,
) : AudioPlaybackManager,
    AudioPlaybackStatusProvider {
    override val playbackStatus: StateFlow<AudioPlaybackStatus>
        get() = _playbackStatus

    private val _playbackStatus = MutableStateFlow(AudioPlaybackStatus())
    private val scope = CoroutineScope(coroutineScope.coroutineContext + progressDispatcher)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var onProgressUpdated: ((Float) -> Unit)? = null
    private var onPlaybackCompleted: (() -> Unit)? = null
    private var currentUri: String? = null
    private var currentMetadata: AudioPlaybackMetadata? = null
    private var skipSilenceEnabled = false
    private var playbackRequest = 0L

    override fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) = startPlayback(uri, metadata, AudioPlaybackOptions(), onProgressUpdated, onPlaybackCompleted)

    /**
     * Starts [uri] at [options]' position and speed, skipping silence when [options] asks. Speed and
     * skip silence are set for every recording, so one never inherits another's.
     */
    fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata?,
        options: AudioPlaybackOptions,
        onProgressUpdated: (Float) -> Unit,
        onPlaybackCompleted: () -> Unit,
    ) {
        this.onProgressUpdated = onProgressUpdated
        this.onPlaybackCompleted = onPlaybackCompleted
        val request = ++playbackRequest
        // startService (not startForegroundService) ensures MediaSessionService.onStartCommand
        // fires so its MediaNotificationManager gets a valid startId. Without this, the service
        // is only bound via BIND_AUTO_CREATE and stops itself ~1s after the controller connects.
        serviceStarter.start(context)
        withController { player ->
            if (request != playbackRequest) return@withController
            currentUri = uri
            currentMetadata = metadata
            this.onProgressUpdated = onProgressUpdated
            this.onPlaybackCompleted = onPlaybackCompleted
            _playbackStatus.value = _playbackStatus.value.copy(errorMessage = null)
            val mediaItem = mediaItemFactory.create(mediaFiles.playableUri(uri), metadata)
            if (options.startPositionMs > 0L) {
                player.setMediaItem(mediaItem, options.startPositionMs)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.setPlaybackSpeed(options.speed)
            player.prepare()
            player.play()
            applySkipSilence(player, options.skipSilence)
            updateStatus(player)
        }
    }

    override fun pausePlayback() {
        withController { player ->
            player.pause()
            updateStatus(player)
        }
    }

    /** Continues a paused track from where it stopped. */
    fun resumePlayback() {
        withController { player ->
            player.play()
            updateStatus(player)
        }
    }

    override fun stopPlayback() {
        val request = ++playbackRequest
        withController { player ->
            if (request != playbackRequest) return@withController
            player.stop()
            currentUri = null
            currentMetadata = null
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

    /** Moves playback to [positionMs], inside the recording once its length is known. */
    fun seekToPosition(positionMs: Long) {
        withController { player ->
            val duration = player.duration
            val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            player.seekTo(target)
            updateStatus(player)
        }
    }

    /** Plays at [speed], where 1 is normal. */
    fun setPlaybackSpeed(speed: Float) {
        withController { player ->
            player.setPlaybackSpeed(speed)
            updateStatus(player)
        }
    }

    /** Skips stretches of silence while [enabled]. */
    fun setSkipSilenceEnabled(enabled: Boolean) {
        withController { player -> applySkipSilence(player, enabled) }
    }

    private fun applySkipSilence(
        player: MediaController,
        enabled: Boolean,
    ) {
        skipSilenceCommand.send(player, enabled) { applied ->
            if (applied) {
                skipSilenceEnabled = enabled
                _playbackStatus.value = _playbackStatus.value.copy(skipSilenceEnabled = enabled)
            } else {
                Napier.w { "The playback session did not turn skip silence ${if (enabled) "on" else "off"}" }
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
                _playbackStatus.value =
                    _playbackStatus.value.copy(
                        isPlaying = false,
                        errorMessage = e.message ?: e::class.simpleName ?: "Unknown playback error",
                    )
                onPlaybackCompleted?.invoke()
            }
        }, controllerExecutor)
    }

    private fun buildControllerFuture(): ListenableFuture<MediaController> = controllerFactory.create(context)

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

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
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

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                controller?.let { updateStatus(it) }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                controller?.let { updateStatus(it) }
            }

            override fun onPlayerError(error: PlaybackException) {
                Napier.e(error) { "Playback failed for $currentUri" }
                stopProgressTracking()
                _playbackStatus.value =
                    _playbackStatus.value.copy(
                        isPlaying = false,
                        errorMessage = error.errorCodeName,
                    )
                onPlaybackCompleted?.invoke()
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
                            _playbackStatus.value =
                                _playbackStatus.value.copy(
                                    progress = normalized,
                                    positionMs = player.currentPosition.coerceAtLeast(0L),
                                )
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
        val positionMs =
            when {
                resetProgress -> 0L
                completed && durationMs > 0 -> durationMs
                else -> player.currentPosition.coerceAtLeast(0L)
            }
        _playbackStatus.value =
            AudioPlaybackStatus(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                progress = progress,
                duration = duration,
                currentUri = currentUri,
                metadata = currentMetadata,
                isSuppressedForUnsuitableOutput =
                    player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT,
                // Preserved rather than reset: onPlayerError sets this outside of updateStatus, and
                // Media3 fires onPlaybackStateChanged/onIsPlayingChanged as part of the same failure
                // transition, which would otherwise clobber the error before anything observes it.
                // startPlayback is the only place that explicitly clears it, for a fresh attempt.
                errorMessage = _playbackStatus.value.errorMessage,
                positionMs = positionMs,
                playbackSpeed = player.playbackParameters.speed,
                skipSilenceEnabled = skipSilenceEnabled,
            )
    }

    private companion object {
        val defaultMediaControllerFutureFactory =
            MediaControllerFutureFactory { context ->
                val token =
                    SessionToken(
                        context,
                        ComponentName(context, AudioPlaybackService::class.java),
                    )
                MediaController.Builder(context, token).buildAsync()
            }

        val defaultMediaItemFactory =
            AudioPlaybackItemFactory { uri, metadata ->
                MediaItem
                    .Builder()
                    .setUri(uri)
                    .setMediaId(metadata?.noteId?.toString() ?: uri)
                    .setMediaMetadata(buildMediaMetadata(metadata))
                    .build()
            }

        val defaultAudioPlaybackServiceStarter =
            AudioPlaybackServiceStarter { context ->
                context.startService(Intent(context, AudioPlaybackService::class.java))
            }

        private val artworkGenerator = AudioNotificationArtworkGenerator()

        fun buildMediaMetadata(metadata: AudioPlaybackMetadata?): MediaMetadata {
            val extras = Bundle()
            metadata?.noteId?.let { extras.putString(EXTRA_NOTE_ID, it.toString()) }
            val builder =
                MediaMetadata
                    .Builder()
                    .setTitle(metadata?.title ?: "Audio")
                    .setSubtitle(metadata?.subtitle)
                    .setExtras(extras.takeIf { !it.isEmpty })

            // Show journal names as the artist field
            val journalNames = metadata?.journalNames.orEmpty()
            if (journalNames.isNotEmpty()) {
                builder.setArtist(journalNames.joinToString(", "))
            }

            // Generate palette-based artwork from daylight colors
            val bg = metadata?.immersiveBackground
            val start = metadata?.gradientStart
            val end = metadata?.gradientEnd
            if (bg != null && start != null && end != null) {
                val artworkBytes = artworkGenerator.generate(bg, start, end)
                builder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            return builder.build()
        }

        fun mainThreadExecutor(): Executor =
            Handler(Looper.getMainLooper()).let { mainHandler ->
                Executor { runnable ->
                    mainHandler.post(runnable)
                }
            }
    }
}
