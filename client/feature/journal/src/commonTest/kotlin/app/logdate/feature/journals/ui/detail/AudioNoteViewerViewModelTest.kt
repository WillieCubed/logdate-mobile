package app.logdate.feature.journals.ui.detail

import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.audio.AudioContextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AudioNoteViewerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun audioNoteLoadsContext() = runTest(dispatcher) {
        val noteId = Uuid.random()
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val note = JournalNote.Audio(
            mediaRef = "audio://test",
            durationMs = 0,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
        )
        val repository = FakeJournalNotesRepository(listOf(note))
        val durationResolver = FakeAudioDurationResolver(durationMs = 5000L)
        val audioPlaybackManager = FakeAudioPlaybackManager()
        val audioContextProcessor = AudioContextProcessor(
            amplitudeExtractor = FakeAmplitudeExtractor(),
            waveformStorage = FakeWaveformStorage(),
            coroutineContext = dispatcher,
        )
        val viewModel = AudioNoteViewerViewModel(
            noteId = noteId,
            notesRepository = repository,
            audioContextProcessor = audioContextProcessor,
            durationResolver = durationResolver,
            audioPlaybackManager = audioPlaybackManager,
        )

        advanceUntilIdle()

        val content = assertIs<AudioNoteViewerUiState.Ready>(viewModel.uiState.value)
        assertEquals("audio://test", content.mediaRef)
        assertEquals(5000L, content.durationMs)
        assertNotNull(content.context)
    }

    @Test
    fun togglePlaybackStartsPlayback() = runTest(dispatcher) {
        val noteId = Uuid.random()
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val note = JournalNote.Audio(
            mediaRef = "audio://test",
            durationMs = 1000L,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
        )
        val repository = FakeJournalNotesRepository(listOf(note))
        val audioPlaybackManager = FakeAudioPlaybackManager()
        val audioContextProcessor = AudioContextProcessor(
            amplitudeExtractor = FakeAmplitudeExtractor(),
            waveformStorage = FakeWaveformStorage(),
            coroutineContext = dispatcher,
        )
        val viewModel = AudioNoteViewerViewModel(
            noteId = noteId,
            notesRepository = repository,
            audioContextProcessor = audioContextProcessor,
            durationResolver = FakeAudioDurationResolver(durationMs = 1000L),
            audioPlaybackManager = audioPlaybackManager,
        )

        advanceUntilIdle()

        viewModel.togglePlayback()
        advanceUntilIdle()

        assertEquals(1, audioPlaybackManager.startCalls)
        assertEquals("audio://test", audioPlaybackManager.lastUri)
        assertEquals(noteId, audioPlaybackManager.lastMetadata?.noteId)
    }

    @Test
    fun seekClampsProgress() = runTest(dispatcher) {
        val noteId = Uuid.random()
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val note = JournalNote.Audio(
            mediaRef = "audio://test",
            durationMs = 1000L,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
        )
        val repository = FakeJournalNotesRepository(listOf(note))
        val audioPlaybackManager = FakeAudioPlaybackManager()
        val audioContextProcessor = AudioContextProcessor(
            amplitudeExtractor = FakeAmplitudeExtractor(),
            waveformStorage = FakeWaveformStorage(),
            coroutineContext = dispatcher,
        )
        val viewModel = AudioNoteViewerViewModel(
            noteId = noteId,
            notesRepository = repository,
            audioContextProcessor = audioContextProcessor,
            durationResolver = FakeAudioDurationResolver(durationMs = 1000L),
            audioPlaybackManager = audioPlaybackManager,
        )

        advanceUntilIdle()

        viewModel.seekTo(1.5f)
        advanceUntilIdle()

        assertEquals(1f, audioPlaybackManager.lastSeek)
    }

    @Test
    fun playbackStatusUpdatesState() = runTest(dispatcher) {
        val noteId = Uuid.random()
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val note = JournalNote.Audio(
            mediaRef = "audio://test",
            durationMs = 1000L,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
        )
        val repository = FakeJournalNotesRepository(listOf(note))
        val audioPlaybackManager = FakeAudioPlaybackManager()
        val audioContextProcessor = AudioContextProcessor(
            amplitudeExtractor = FakeAmplitudeExtractor(),
            waveformStorage = FakeWaveformStorage(),
            coroutineContext = dispatcher,
        )
        val viewModel = AudioNoteViewerViewModel(
            noteId = noteId,
            notesRepository = repository,
            audioContextProcessor = audioContextProcessor,
            durationResolver = FakeAudioDurationResolver(durationMs = 1000L),
            audioPlaybackManager = audioPlaybackManager,
        )

        advanceUntilIdle()

        audioPlaybackManager.updateStatus(
            audioPlaybackManager.playbackStatus.value.copy(
                isPlaying = true,
                progress = 0.5f,
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val ready = assertIs<AudioNoteViewerUiState.Ready>(state)
        assertTrue(ready.playbackState.isPlaying)
        assertEquals(0.5f, ready.playbackState.progress)
    }
}
