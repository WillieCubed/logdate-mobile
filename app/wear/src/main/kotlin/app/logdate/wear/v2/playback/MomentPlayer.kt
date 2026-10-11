package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioNotOnWatchException
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.NoRoomForAudioException
import app.logdate.wear.playback.WearAudioOutputs
import app.logdate.wear.playback.WearSyncedAudioResolver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Plays one recording at a time for every screen of the new watch app, so a row in a list and a
 * moment's own screen always agree on what is playing.
 *
 * A recording the watch does not have is fetched from the phone first. Nothing starts without a
 * speaker or headphones, and playback the system refuses on the current output stops and says so,
 * so a recording never appears to play in silence. Speed and skip silence apply to the recordings
 * after this one too, through [PlaybackPreferencesStore].
 */
class MomentPlayer(
    private val scope: CoroutineScope,
    private val engine: MomentPlaybackEngine,
    private val outputs: WearAudioOutputs,
    private val resolver: WearSyncedAudioResolver,
    private val preferences: PlaybackPreferencesStore,
) {
    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private var resolveJob: Job? = null

    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    val speed: StateFlow<PlaybackSpeed> = preferences.speed
    val skipSilence: StateFlow<Boolean> = preferences.skipSilence

    init {
        scope.launch { engine.status.collect(::onEngineStatus) }
    }

    /** Plays [note] from the start, or pauses or resumes it when it is the recording already playing. */
    fun playOrPause(note: JournalNote.Audio) {
        val current = mutableState.value
        if (current is PlaybackState.Active && current.noteId == note.uid) {
            togglePause(current)
            return
        }
        if (current is PlaybackState.Preparing && current.noteId == note.uid) return
        start(note)
    }

    fun seekBy(deltaMs: Long) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        seekTo(current.positionMs + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        val target = if (current.durationMs > 0L) positionMs.coerceIn(0L, current.durationMs) else positionMs.coerceAtLeast(0L)
        engine.seekTo(target)
        mutableState.value = current.copy(positionMs = target)
    }

    /** Steps to the next speed, for this recording and the ones after it. */
    fun cycleSpeed() {
        val next = preferences.speed.value.next()
        preferences.setSpeed(next)
        if (mutableState.value is PlaybackState.Active) engine.setSpeed(next.factor)
    }

    fun setSkipSilence(enabled: Boolean) {
        preferences.setSkipSilence(enabled)
        if (mutableState.value is PlaybackState.Active) engine.setSkipSilence(enabled)
    }

    fun stop() {
        resolveJob?.cancel()
        resolveJob = null
        if (mutableState.value != PlaybackState.Idle) engine.stop()
        mutableState.value = PlaybackState.Idle
    }

    /** Stops playback when it is [noteId]'s, for a recording that is going away. */
    fun stopIfPlaying(noteId: Uuid) {
        if (mutableState.value.noteId == noteId) stop()
    }

    private fun start(note: JournalNote.Audio) {
        resolveJob?.cancel()
        if (mutableState.value is PlaybackState.Active) engine.stop()
        if (outputs.outputState.value is AudioOutputState.Unavailable) {
            Napier.w("No speaker or headphones to play note ${note.uid} on")
            mutableState.value = PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT)
            return
        }
        mutableState.value = PlaybackState.Preparing(note.uid)
        resolveJob = scope.launch { resolveAndStart(note) }
    }

    private suspend fun resolveAndStart(note: JournalNote.Audio) {
        val uri =
            resolver.resolvePlayableUri(note).getOrElse { error ->
                if (isPreparing(note.uid)) mutableState.value = PlaybackState.Failed(note.uid, error.toPlaybackFailure())
                return
            }
        if (!isPreparing(note.uid)) return
        mutableState.value = PlaybackState.Active(noteId = note.uid, positionMs = 0L, durationMs = note.durationMs, isPaused = false)
        val options = AudioPlaybackOptions(speed = speed.value.factor, skipSilence = skipSilence.value)
        engine.start(uri, note.uid, options) { failed -> onFinished(note.uid, failed) }
    }

    private fun isPreparing(noteId: Uuid): Boolean = (mutableState.value as? PlaybackState.Preparing)?.noteId == noteId

    private fun togglePause(current: PlaybackState.Active) {
        if (current.isPaused) engine.resume() else engine.pause()
        mutableState.value = current.copy(isPaused = !current.isPaused)
    }

    private fun onFinished(
        noteId: Uuid,
        failed: Boolean,
    ) {
        if (mutableState.value.noteId != noteId) return
        mutableState.value = if (failed) PlaybackState.Failed(noteId, PlaybackFailure.UNPLAYABLE) else PlaybackState.Idle
    }

    private fun onEngineStatus(status: EngineStatus) {
        val current = mutableState.value as? PlaybackState.Active ?: return
        if (status.noteId != current.noteId) return
        if (status.suppressedForUnsuitableOutput && status.playWhenReady) {
            engine.stop()
            mutableState.value = PlaybackState.Failed(current.noteId, PlaybackFailure.NO_OUTPUT)
            return
        }
        mutableState.value =
            current.copy(
                positionMs = status.positionMs,
                isPaused = !status.playWhenReady,
                durationMs = status.durationMs.takeIf { it > 0L } ?: current.durationMs,
            )
    }
}

private fun Throwable.toPlaybackFailure(): PlaybackFailure =
    when (this) {
        is AudioNotOnWatchException -> PlaybackFailure.NOT_ON_WATCH
        is NoRoomForAudioException -> PlaybackFailure.NO_ROOM
        else -> PlaybackFailure.UNPLAYABLE
    }
