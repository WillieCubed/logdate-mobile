package app.logdate.feature.journals.ui.detail

import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.ui.audio.AudioContextProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AudioDurationViewerRepairTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-12T17:00:00Z")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun actualResolvedDurationIsSentToConditionalDurableRepair() =
        runTest(dispatcher) {
            val note = audio(0)
            val calls = mutableListOf<Triple<Uuid, String, Long>>()
            val repository =
                object : JournalNotesRepository by FakeJournalNotesRepository(listOf(note)) {
                    override suspend fun updateAudioDurationIfUnknown(
                        noteId: Uuid,
                        mediaRef: String,
                        durationMs: Long,
                    ): Boolean {
                        calls += Triple(noteId, mediaRef, durationMs)
                        return true
                    }
                }
            val model = viewer(note, repository)
            advanceUntilIdle()
            assertEquals(listOf(Triple(note.uid, note.mediaRef, 3_888L)), calls)
            assertEquals(3_888L, assertIs<AudioNoteViewerUiState.Ready>(model.uiState.value).durationMs)
        }

    @Test
    fun knownDurationDoesNotWriteOrReplaceRetainedMetadata() =
        runTest(dispatcher) {
            val note = audio(7_500)
            val repository =
                object : JournalNotesRepository by FakeJournalNotesRepository(listOf(note)) {
                    override suspend fun updateAudioDurationIfUnknown(
                        noteId: Uuid,
                        mediaRef: String,
                        durationMs: Long,
                    ): Boolean = error("Known duration must not be rewritten")
                }
            val model = viewer(note, repository)
            advanceUntilIdle()
            assertEquals(7_500L, assertIs<AudioNoteViewerUiState.Ready>(model.uiState.value).durationMs)
        }

    private fun audio(duration: Long) =
        JournalNote.Audio(
            mediaRef = "file:///retained/shared.wav",
            durationMs = duration,
            creationTimestamp = now,
            lastUpdated = now,
        )

    private fun viewer(
        note: JournalNote.Audio,
        repository: JournalNotesRepository,
    ) = AudioNoteViewerViewModel(
        noteId = note.uid,
        notesRepository = repository,
        audioContextProcessor =
            AudioContextProcessor(
                amplitudeExtractor = FakeAmplitudeExtractor(),
                waveformStorage = FakeWaveformStorage(),
                coroutineContext = dispatcher,
            ),
        durationResolver = FakeAudioDurationResolver(3_888),
        audioPlaybackManager = FakeAudioPlaybackManager(),
        getJournalMembership = GetJournalMembershipUseCase(FakeDetailJournalContentRepository()),
        audioTagRepository = FakeAudioTagRepository(),
    )
}
