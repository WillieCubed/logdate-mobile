package app.logdate.client.media.audio

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * The custom command LogDate's playback session accepts beside the standard player commands.
 *
 * Speed is a standard player command. Skip silence is an ExoPlayer setting with no player command,
 * so a controller asks the session for it with [SET_SKIP_SILENCE] and [EXTRA_ENABLED].
 */
object PlaybackSessionCommands {
    const val ACTION_SET_SKIP_SILENCE = "app.logdate.audio.SET_SKIP_SILENCE"
    const val EXTRA_ENABLED = "enabled"

    val SET_SKIP_SILENCE: SessionCommand by lazy { SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY) }

    fun skipSilenceArgs(enabled: Boolean): Bundle = Bundle().apply { putBoolean(EXTRA_ENABLED, enabled) }
}

/**
 * Offers [PlaybackSessionCommands.SET_SKIP_SILENCE] to every controller and applies it with
 * [setSkipSilence]. A request without [PlaybackSessionCommands.EXTRA_ENABLED] is refused.
 */
class PlaybackSessionCallback(
    private val setSkipSilence: (Boolean) -> Unit,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(PlaybackSessionCommands.SET_SKIP_SILENCE)
                    .build(),
            ).build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != PlaybackSessionCommands.ACTION_SET_SKIP_SILENCE) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        if (!args.containsKey(PlaybackSessionCommands.EXTRA_ENABLED)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        setSkipSilence(args.getBoolean(PlaybackSessionCommands.EXTRA_ENABLED))
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}

/** Asks a session to turn skip silence on or off; [send]'s `onResult` receives whether it did. */
fun interface SkipSilenceCommand {
    fun send(
        controller: MediaController,
        enabled: Boolean,
        onResult: (Boolean) -> Unit,
    )

    companion object {
        /** Sends [PlaybackSessionCommands.SET_SKIP_SILENCE] to the session behind the controller. */
        val Session =
            SkipSilenceCommand { controller, enabled, onResult ->
                val result =
                    controller.sendCustomCommand(
                        PlaybackSessionCommands.SET_SKIP_SILENCE,
                        PlaybackSessionCommands.skipSilenceArgs(enabled),
                    )
                result.addListener(
                    { onResult(runCatching { result.get().resultCode == SessionResult.RESULT_SUCCESS }.getOrDefault(false)) },
                    MoreExecutors.directExecutor(),
                )
            }
    }
}
