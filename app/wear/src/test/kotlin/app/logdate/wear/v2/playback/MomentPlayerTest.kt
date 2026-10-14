package app.logdate.wear.v2.playback

import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioNotOnWatchException
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.playback.FakeOutputs
import app.logdate.wear.playback.FakeResolver
import app.logdate.wear.playback.NoRoomForAudioException
import app.logdate.wear.playback.WearSyncedAudioResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MomentPlayerTest {
    private val engine = FakeMomentEngine()
    private val outputs = FakeOutputs()
    private val resolver = FakeResolver()
    private val preferences = FakePlaybackPreferences()
    private val note = voiceNote()
    private val other = voiceNote(durationMs = 10_000L)

    @Test
    fun `external resume on an unsuitable output reports failure`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)
            engine.mutableStatus.value = EngineStatus(note.uid, 100L, 42_000L, false, playWhenReady = false)
            engine.mutableStatus.value = EngineStatus(note.uid, 100L, 42_000L, true, playWhenReady = true)
            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT), player.state.value)
        }

    @Test
    fun `external pause does not become an output failure`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)
            engine.mutableStatus.value = EngineStatus(note.uid, 100L, 42_000L, true, playWhenReady = false)
            assertEquals(true, (player.state.value as PlaybackState.Active).isPaused)
        }

    @Test
    fun `system pause and resume reconcile the watch controls`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)
            engine.mutableStatus.value = EngineStatus(note.uid, 100L, 42_000L, false, playWhenReady = false)
            assertEquals(true, (player.state.value as PlaybackState.Active).isPaused)
            player.playOrPause(note)
            assertEquals(1, engine.resumes)
            engine.mutableStatus.value = EngineStatus(note.uid, 200L, 42_000L, false, playWhenReady = true)
            assertEquals(false, (player.state.value as PlaybackState.Active).isPaused)
        }

    @Test
    fun `a voice note plays at the saved speed and skip silence`() =
        runTest(UnconfinedTestDispatcher()) {
            preferences.speed.value = PlaybackSpeed.FASTER
            preferences.skipSilence.value = true

            player().playOrPause(note)

            assertEquals(listOf("/watch/${note.uid}.m4a" to AudioPlaybackOptions(speed = 1.5f, skipSilence = true)), engine.starts)
        }

    @Test
    fun `a playing note is active from its start`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Active(note.uid, positionMs = 0L, durationMs = 42_000L, isPaused = false), player.state.value)
        }

    @Test
    fun `playing the note again pauses it and once more resumes it`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)
            player.playOrPause(note)
            assertEquals(true, (player.state.value as PlaybackState.Active).isPaused)
            player.playOrPause(note)

            assertEquals(1, engine.pauses)
            assertEquals(1, engine.resumes)
            assertEquals(false, (player.state.value as PlaybackState.Active).isPaused)
        }

    @Test
    fun `nothing plays without a speaker or headphones`() =
        runTest(UnconfinedTestDispatcher()) {
            outputs.state.value = AudioOutputState.Unavailable
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT), player.state.value)
            assertTrue(resolver.resolved.isEmpty())
        }

    @Test
    fun `audio on an unreachable phone fails as not on the watch`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = AudioNotOnWatchException(note.uid)
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NOT_ON_WATCH), player.state.value)
            assertTrue(engine.starts.isEmpty())
        }

    @Test
    fun `resolver failure reports its stage and class without its private message`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = java.io.IOException("private media reference")
            val diagnostics = mutableListOf<Pair<PlaybackFailureStage, String>>()

            player(diagnose = { stage, code -> diagnostics += stage to code }).playOrPause(note)

            assertEquals(listOf(PlaybackFailureStage.RESOLUTION to "IOException"), diagnostics)
            assertTrue(engine.starts.isEmpty())
        }

    @Test
    fun `engine failure reports its own stage after successful resolution`() =
        runTest(UnconfinedTestDispatcher()) {
            val diagnostics = mutableListOf<Pair<PlaybackFailureStage, String>>()
            player(diagnose = { stage, code -> diagnostics += stage to code }).playOrPause(note)

            engine.finish(failed = true)

            assertEquals(listOf(PlaybackFailureStage.ENGINE to "PLAYBACK_FAILED"), diagnostics)
        }

    @Test
    fun `a full watch fails as out of room`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = NoRoomForAudioException(freeBytes = 1L, reserveBytes = 2L)
            val player = player()

            player.playOrPause(note)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_ROOM), player.state.value)
        }

    @Test
    fun `the engine's position moves the playing note and no other`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.mutableStatus.value =
                EngineStatus(other.uid, positionMs = 9_000L, durationMs = 10_000L, suppressedForUnsuitableOutput = false)
            engine.mutableStatus.value =
                EngineStatus(note.uid, positionMs = 12_000L, durationMs = 41_500L, suppressedForUnsuitableOutput = false)

            assertEquals(PlaybackState.Active(note.uid, positionMs = 12_000L, durationMs = 41_500L, isPaused = false), player.state.value)
        }

    @Test
    fun `seeking stays inside the recording`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.seekBy(-5_000L)
            player.seekTo(50_000L)

            assertEquals(listOf(0L, 42_000L), engine.seeks)
        }

    @Test
    fun `a new speed is saved and applies only while playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.cycleSpeed()
            assertEquals(PlaybackSpeed.FASTER, preferences.speed.value)
            assertTrue(engine.speeds.isEmpty())
            player.playOrPause(note)
            player.cycleSpeed()

            assertEquals(PlaybackSpeed.FASTEST, player.speed.value)
            assertEquals(listOf(2f), engine.speeds)
        }

    @Test
    fun `skip silence is saved and applies while playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.setSkipSilence(true)

            assertEquals(true, player.skipSilence.value)
            assertEquals(listOf(true), engine.skipSilences)
        }

    @Test
    fun `playback the system refuses on this output stops and says so`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.mutableStatus.value = EngineStatus(note.uid, positionMs = 0L, durationMs = 42_000L, suppressedForUnsuitableOutput = true)

            assertEquals(1, engine.stops)
            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NO_OUTPUT), player.state.value)
        }

    @Test
    fun `an ended recording returns to idle and a failed one says so`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            engine.finish()
            assertEquals(PlaybackState.Idle, player.state.value)
            player.playOrPause(note)
            engine.finish(failed = true)

            assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.UNPLAYABLE), player.state.value)
        }

    @Test
    fun `playing another note stops the first`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()

            player.playOrPause(note)
            player.playOrPause(other)

            assertEquals(1, engine.stops)
            assertEquals(2, engine.starts.size)
            assertEquals(other.uid, player.state.value.noteId)
        }

    @Test
    fun `stopping for a note that is going away leaves other playback alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            player.playOrPause(note)

            player.stopIfPlaying(other.uid)
            assertEquals(note.uid, player.state.value.noteId)
            player.stopIfPlaying(note.uid)

            assertEquals(PlaybackState.Idle, player.state.value)
            assertEquals(1, engine.stops)
        }

    @Test
    fun `a recording replaced while it is fetched leaves the new one alone`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slowResolver =
                object : WearSyncedAudioResolver {
                    override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> =
                        runCatching {
                            gate.await()
                            "/watch/${note.uid}.m4a"
                        }
                }
            val player = player(slowResolver)
            player.playOrPause(note)
            runCurrent()

            player.playOrPause(other)
            runCurrent()

            assertEquals(PlaybackState.Preparing(other.uid), player.state.value)
        }

    @Test
    fun `continuing playback starts at the requested position and waits for audible playback`() =
        runTest {
            val player = player()
            val request = async { player.continuePlayback(note, 12_000, 1.5f) }
            runCurrent()
            assertEquals(
                12_000L,
                engine.starts
                    .single()
                    .second.startPositionMs,
            )
            assertEquals(
                1.5f,
                engine.starts
                    .single()
                    .second.speed,
            )
            assertFalse(request.isCompleted)
            engine.mutableStatus.value = EngineStatus(note.uid, 12_000, 42_000, false, isPlaying = true)
            runCurrent()
            assertTrue(request.await())
        }

    @Test
    fun `continuing playback which never starts fails and stops the pending player`() =
        runTest {
            val player = player()
            assertFalse(player.continuePlayback(note, 12_000, 1f))
            assertEquals(PlaybackState.Idle, player.state.value)
            assertEquals(1, engine.stops)
        }

    private fun TestScope.player(
        resolver: WearSyncedAudioResolver = this@MomentPlayerTest.resolver,
        diagnose: (PlaybackFailureStage, String) -> Unit = { _, _ -> },
    ) = MomentPlayer(
        scope = backgroundScope,
        engine = engine,
        outputs = outputs,
        resolver = resolver,
        preferences = preferences,
        diagnose = diagnose,
    )
}
