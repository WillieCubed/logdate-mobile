package app.logdate.wear.capture

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.Stamped
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchCapturePreferencesTest {
    private val shared = mockk<StampedPreferenceStore>()

    @Test
    fun `capture waits for retained preferences instead of using an initial UI default`() =
        runTest {
            val loaded = CompletableDeferred<Map<String, Stamped>>()
            coEvery { shared.snapshot() } coAnswers { loaded.await() }
            val preferences = WatchCapturePreferences(shared) { true }
            val choice = async { preferences.moodAfterRecording() }
            runCurrent()
            assertFalse(choice.isCompleted)
            val key = SharedPreferenceKey.MoodAfterRecording
            loaded.complete(mapOf(key.name to Stamped(key.encode(false), 1)))
            assertFalse(choice.await())
        }

    @Test
    fun `rollout off preserves legacy capture behavior without reading shared choices`() =
        runTest {
            val preferences = WatchCapturePreferences(shared) { false }
            assertNull(preferences.capturePlaces())
            assertTrue(preferences.moodAfterRecording())
        }

    @Test
    fun `fresh rollout keeps places off and mood on`() =
        runTest {
            coEvery { shared.snapshot() } returns emptyMap()
            val preferences = WatchCapturePreferences(shared) { true }
            assertFalse(preferences.capturePlaces()!!)
            assertTrue(preferences.moodAfterRecording())
        }
}
