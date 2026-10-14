package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.recording.FakeWearRecorder
import app.logdate.wear.v2.haptics.RecordedHaptics
import app.logdate.wear.v2.permissions.PermissionPrompts
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RecorderCapturePreferencesTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `saved recording reads current persisted mood choice before offering feelings`() =
        runTest {
            try {
                var enabled = true
                val recorder = FakeWearRecorder()
                val notes = mockk<JournalNotesRepository>(relaxed = true)
                coEvery { notes.create(any<JournalNote>()) } returns Uuid.random()
                val duration = mockk<AudioDurationResolver>()
                coEvery { duration.resolveDurationMs(any()) } returns 2_000L
                val location = mockk<WearLocationCaptureCoordinator>()
                coEvery { location.captureForJournalEntry() } returns null
                val model =
                    RecorderViewModel(
                        recorder,
                        AudioMomentSaver(notes, duration, mockk<NoteHealthAnnotator>(relaxed = true), location),
                        notes,
                        {},
                        { true },
                        RecordedHaptics(),
                        PermissionPrompts({ true }, mockk(relaxed = true)),
                        backgroundScope,
                        moodAfterRecording = { enabled },
                    )
                val factory =
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    }
                ViewModelProvider(models, factory)[RecorderViewModel::class.java]
                model.onPressed()
                runCurrent()
                model.onTapped()
                runCurrent()
                enabled = false
                recorder.elapsedFlow.value = 2.seconds
                model.stop()
                runCurrent()
                assertFalse(assertIs<RecorderState.Saved>(model.state.value).feelingsOffered)
            } finally {
                models.clear()
                runCurrent()
            }
        }
}
