package app.logdate.wear.v2.moments

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.wear.playback.AudioNotOnWatchException
import app.logdate.wear.playback.FakeOutputs
import app.logdate.wear.playback.FakeResolver
import app.logdate.wear.sync.WearNoteRemovalNotifier
import app.logdate.wear.v2.playback.FakeMomentEngine
import app.logdate.wear.v2.playback.FakePlaybackPreferences
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.NOTE_TIME
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.voiceNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MomentViewModelTest {
    private val note = voiceNote()
    private val text = JournalNote.Text(creationTimestamp = NOTE_TIME, lastUpdated = NOTE_TIME, content = "Coffee at the lake")
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val haptics = mockk<LogDateHaptics>(relaxed = true)
    private val engine = FakeMomentEngine()
    private val resolver = FakeResolver()
    private val zone = TimeZone.of("Europe/Berlin")
    private val phoneTold = mutableListOf<Uuid>()
    private val audioDiscarded = mutableListOf<JournalNote.Audio>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { notesRepository.getNoteById(any()) } returns null
        coEvery { notesRepository.getNoteById(note.uid) } returns note
        coEvery { notesRepository.getNoteById(text.uid) } returns text
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a voice note opens ready to play`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel(player()).uiState.test {
                assertEquals(
                    MomentUiState.Ready(note, zone, PlaybackState.Idle, PlaybackSpeed.NORMAL, skipSilence = false),
                    expectMostRecentItem(),
                )
            }
        }

    @Test
    fun `a text moment opens on its details`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel(player(), id = text.uid).uiState.test {
                assertEquals(text, (expectMostRecentItem() as MomentUiState.Ready).note)
            }
        }

    @Test
    fun `an unknown or unreadable ID is missing`() =
        runTest(UnconfinedTestDispatcher()) {
            viewModel(player(), id = Uuid.random()).uiState.test { assertEquals(MomentUiState.Missing, expectMostRecentItem()) }
            viewModel(player(), id = null).uiState.test { assertEquals(MomentUiState.Missing, expectMostRecentItem()) }
        }

    @Test
    fun `a moment that cannot be read is missing`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { notesRepository.getNoteById(note.uid) } throws IllegalStateException("database closed")

            viewModel(player()).uiState.test { assertEquals(MomentUiState.Missing, expectMostRecentItem()) }
        }

    @Test
    fun `play starts this note and the state follows it`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.playPause()

                val playback = (expectMostRecentItem() as MomentUiState.Ready).playback
                assertEquals(PlaybackState.Active(note.uid, positionMs = 0L, durationMs = 42_000L, isPaused = false), playback)
            }
        }

    @Test
    fun `play does nothing for a moment without a recording`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player(), id = text.uid)

            viewModel.playPause()

            assertTrue(engine.starts.isEmpty())
        }

    @Test
    fun `a recording that cannot be played says why`() =
        runTest(UnconfinedTestDispatcher()) {
            resolver.failure = AudioNotOnWatchException(note.uid)
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.playPause()

                val playback = (expectMostRecentItem() as MomentUiState.Ready).playback
                assertEquals(PlaybackState.Failed(note.uid, PlaybackFailure.NOT_ON_WATCH), playback)
            }
        }

    @Test
    fun `another recording playing shows this one idle`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = player()
            val viewModel = viewModel(player)

            viewModel.uiState.test {
                player.playOrPause(voiceNote(durationMs = 5_000L))

                assertEquals(PlaybackState.Idle, (expectMostRecentItem() as MomentUiState.Ready).playback)
            }
        }

    @Test
    fun `speed and skip silence follow the player`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.cycleSpeed()
                viewModel.setSkipSilence(true)

                val ready = expectMostRecentItem() as MomentUiState.Ready
                assertEquals(PlaybackSpeed.FASTER, ready.speed)
                assertTrue(ready.skipSilence)
            }
        }

    @Test
    fun `the crown seeks in 5 second steps with one tick a turn`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())
            viewModel.playPause()

            viewModel.onCrown(60f)
            viewModel.onCrown(30f)

            assertEquals(listOf(5_000L), engine.seeks)
            verify(exactly = 1) { haptics.detentTick() }
        }

    @Test
    fun `turning the crown back seeks back`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())
            viewModel.playPause()
            viewModel.onCrown(180f)

            viewModel.onCrown(-60f)

            assertEquals(listOf(15_000L, 10_000L), engine.seeks)
        }

    @Test
    fun `the crown does nothing while this note is not playing`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())

            viewModel.onCrown(120f)

            assertTrue(engine.seeks.isEmpty())
            verify(exactly = 0) { haptics.detentTick() }
        }

    @Test
    fun `part turns of the crown while nothing plays do not count toward the first step`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())
            viewModel.onCrown(40f)
            viewModel.playPause()

            viewModel.onCrown(40f)

            assertTrue(engine.seeks.isEmpty())
        }

    @Test
    fun `deleting removes the note and its playback and closes the screen`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())
            viewModel.playPause()

            viewModel.uiState.test {
                viewModel.delete()

                assertEquals(MomentUiState.Missing, expectMostRecentItem())
            }
            coVerify { notesRepository.remove(note) }
            assertEquals(1, engine.stops)
            verify { haptics.confirmDestruction() }
            assertTrue(viewModel.deleted.value)
        }

    @Test
    fun `deleting tells the phone and drops the audio stored on the watch`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player())

            viewModel.delete()

            assertEquals(listOf(note.uid), phoneTold)
            assertEquals(listOf(note), audioDiscarded)
        }

    @Test
    fun `deleting a text moment has no audio to drop`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = viewModel(player(), id = text.uid)

            viewModel.delete()

            coVerify { notesRepository.remove(text) }
            assertEquals(listOf(text.uid), phoneTold)
            assertTrue(audioDiscarded.isEmpty())
        }

    @Test
    fun `deleting twice while the first is still going removes the note once`() =
        runTest(UnconfinedTestDispatcher()) {
            val release = CompletableDeferred<Unit>()
            coEvery { notesRepository.remove(any()) } coAnswers { release.await() }
            val viewModel = viewModel(player())

            viewModel.delete()
            viewModel.delete()
            release.complete(Unit)

            coVerify(exactly = 1) { notesRepository.remove(note) }
            assertEquals(listOf(note.uid), phoneTold)
        }

    @Test
    fun `a failed delete stays open and says so`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { notesRepository.remove(any()) } throws IllegalStateException("database closed")
            val viewModel = viewModel(player())

            viewModel.uiState.test {
                viewModel.delete()

                assertTrue((expectMostRecentItem() as MomentUiState.Ready).deleteFailed)
            }
            assertFalse(viewModel.deleted.value)
            verify { haptics.actionRejected() }
            assertTrue(phoneTold.isEmpty())
            assertTrue(audioDiscarded.isEmpty())
        }

    @Test
    fun `deleting again after a failure tries again`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { notesRepository.remove(any()) } throws IllegalStateException("database closed") andThen Unit
            val viewModel = viewModel(player())

            viewModel.delete()
            viewModel.delete()

            assertTrue(viewModel.deleted.value)
            assertEquals(listOf(note.uid), phoneTold)
        }

    private fun TestScope.player() =
        MomentPlayer(
            scope = backgroundScope,
            engine = engine,
            outputs = FakeOutputs(),
            resolver = resolver,
            preferences = FakePlaybackPreferences(),
        )

    private fun viewModel(
        player: MomentPlayer,
        id: Uuid? = note.uid,
    ) = MomentViewModel(
        noteId = id,
        notesRepository = notesRepository,
        player = player,
        haptics = haptics,
        removalNotifier = WearNoteRemovalNotifier { noteId -> phoneTold += noteId },
        discardAudio = { audio -> audioDiscarded += audio },
        timeZone = { zone },
    )
}
