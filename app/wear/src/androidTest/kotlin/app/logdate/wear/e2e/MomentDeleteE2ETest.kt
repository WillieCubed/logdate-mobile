package app.logdate.wear.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.v2.moments.MomentTags
import app.logdate.wear.v2.today.TodayTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Deletes a voice moment from its screen in the real activity and graph: the note and the audio
 * file on the watch go, and the screen closes back to Today.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MomentDeleteE2ETest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val koin get() = KoinJavaComponent.getKoin()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openTheNewWatchApp() {
        setNewWatchApp(enabled = true)
    }

    @After
    fun tearDown() {
        scenario?.onActivity { it.finish() }
        setNewWatchApp(enabled = false)
    }

    @Test
    fun deleting_a_voice_moment_removes_the_note_and_its_audio_and_closes_the_screen() {
        val mediaFiles = koin.get<MediaFileResolver>()
        val recordings = File(mediaFiles.directory(MediaCollection.Recordings)).apply { mkdirs() }
        val audio = File(recordings, "moment-delete-${Uuid.random()}.m4a").apply { writeText("audio") }
        val now = Clock.System.now()
        val note =
            JournalNote.Audio(
                mediaRef = mediaFiles.storedReference(audio.absolutePath),
                durationMs = 5_000L,
                creationTimestamp = now,
                lastUpdated = now,
                transcription = "A recording to delete",
            )
        val notes = koin.get<JournalNotesRepository>()
        runBlocking { notes.create(note) }
        try {
            val link =
                Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
                    .putExtra(WearLaunchRequest.EXTRA_ROUTE, "moment")
                    .putExtra(WearLaunchRequest.EXTRA_NOTE_ID, note.uid.toString())
            scenario = ActivityScenario.launch(link)

            waitFor { composeRule.onAllNodesWithTag(MomentTags.PLAYER) }
            composeRule.onNodeWithTag(MomentTags.SCREEN).performTouchInput { swipeUp() }
            waitFor { composeRule.onAllNodesWithTag(MomentTags.DETAILS) }
            composeRule.onNodeWithTag(MomentTags.DETAILS).performRotaryScrollInput { rotateToScrollVertically(LONG_SCROLL) }
            waitFor { composeRule.onAllNodesWithTag(MomentTags.DELETE) }
            composeRule.onNodeWithTag(MomentTags.DELETE).performClick()
            waitFor { composeRule.onAllNodesWithTag(MomentTags.CONFIRM_DELETE) }
            composeRule.onNodeWithTag(MomentTags.CONFIRM_DELETE).performClick()

            composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithTag(MomentTags.SCREEN).fetchSemanticsNodes().isEmpty() }
            waitFor { composeRule.onAllNodesWithTag(TodayTags.PAGE) }
            assertNull(runBlocking { notes.getNoteById(note.uid) })
            assertFalse(audio.exists(), "the audio file is still on the watch")
        } finally {
            runBlocking { notes.removeById(note.uid) }
            audio.delete()
        }
    }

    private fun setNewWatchApp(enabled: Boolean) =
        runBlocking {
            koin.get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        }

    private fun waitFor(nodes: () -> SemanticsNodeInteractionCollection) {
        composeRule.waitUntil(TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val LONG_SCROLL = 600f
        const val TIMEOUT_MS = 10_000L
    }
}
