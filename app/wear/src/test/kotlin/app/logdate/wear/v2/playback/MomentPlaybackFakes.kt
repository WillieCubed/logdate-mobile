package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class FakeMomentEngine : MomentPlaybackEngine {
    val starts = mutableListOf<Pair<String, AudioPlaybackOptions>>()
    val seeks = mutableListOf<Long>()
    val speeds = mutableListOf<Float>()
    val skipSilences = mutableListOf<Boolean>()
    var pauses = 0
    var resumes = 0
    var stops = 0
    val mutableStatus = MutableStateFlow(EngineStatus.Idle)
    private var onFinished: (Boolean) -> Unit = {}

    override val status: StateFlow<EngineStatus> = mutableStatus

    override fun start(
        uri: String,
        noteId: Uuid,
        options: AudioPlaybackOptions,
        onFinished: (failed: Boolean) -> Unit,
    ) {
        starts += uri to options
        this.onFinished = onFinished
    }

    override fun pause() {
        pauses++
    }

    override fun resume() {
        resumes++
    }

    override fun stop() {
        stops++
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun setSpeed(speed: Float) {
        speeds += speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        skipSilences += enabled
    }

    fun finish(failed: Boolean = false) = onFinished(failed)
}

internal class FakePlaybackPreferences(
    speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    skipSilence: Boolean = false,
) : PlaybackPreferencesStore {
    override val speed = MutableStateFlow(speed)
    override val skipSilence = MutableStateFlow(skipSilence)

    override fun setSpeed(speed: PlaybackSpeed) {
        this.speed.value = speed
    }

    override fun setSkipSilence(enabled: Boolean) {
        skipSilence.value = enabled
    }
}

internal val NOTE_TIME: Instant = Instant.fromEpochMilliseconds(1_791_000_000_000)

internal fun voiceNote(
    durationMs: Long = 42_000L,
    transcript: TranscriptDocument? = null,
    transcription: String = "",
): JournalNote.Audio =
    JournalNote.Audio(
        mediaRef = "content://phone/audio/walk.m4a",
        durationMs = durationMs,
        creationTimestamp = NOTE_TIME,
        lastUpdated = NOTE_TIME,
        transcript = transcript,
        transcription = transcription,
    )
