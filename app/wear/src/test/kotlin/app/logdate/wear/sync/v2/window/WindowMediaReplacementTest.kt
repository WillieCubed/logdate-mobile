package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowMediaReplacementTest {
    private val f = WindowFixture()
    private val audio =
        JournalNote.Audio(
            uid = f.note.uid,
            mediaRef = "phone.m4a",
            durationMs = 100,
            creationTimestamp = f.note.creationTimestamp,
            lastUpdated = f.note.lastUpdated,
        )

    private suspend fun seed() {
        f.applier.reconcile(f.peer, f.response(listOf(audio)), listOf(f.item(audio)))
    }

    @Test fun `same timestamp media replacement invalidates old downloaded bytes`() =
        runTest {
            seed()
            val replacement = audio.copy(mediaRef = "replacement.m4a")
            assertTrue(f.applier.onItem(f.item(replacement, 2)))
            assertEquals(listOf(audio.uid), f.forgotten)
            assertEquals(replacement, f.stored[audio.uid])
        }

    @Test fun `changing an audio note to text invalidates downloaded audio`() =
        runTest {
            seed()
            assertTrue(f.applier.onItem(f.item(f.note, 2)))
            assertEquals(listOf(audio.uid), f.forgotten)
        }

    @Test fun `same version caption changes preserve verified audio bytes`() =
        runTest {
            seed()
            assertTrue(f.applier.onItem(f.item(audio.copy(caption = "new caption"), 2)))
            assertTrue(f.forgotten.isEmpty())
        }

    @Test fun `a protected local edit never loses its downloaded audio`() =
        runTest {
            seed()
            f.stored[audio.uid] = audio.copy(caption = "watch edit")
            assertFalse(f.applier.onItem(f.item(audio.copy(mediaRef = "replacement.m4a"), 2)))
            assertTrue(f.forgotten.isEmpty())
        }
}
