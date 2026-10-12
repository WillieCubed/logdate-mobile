package app.logdate.wear.playback

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WindowPhoneAudioResolverTest {
    private val note =
        JournalNote.Audio(
            uid = Uuid.random(),
            mediaRef = "phone.m4a",
            durationMs = 100,
            creationTimestamp = kotlin.time.Instant.fromEpochMilliseconds(1),
            lastUpdated = kotlin.time.Instant.fromEpochMilliseconds(1),
        )
    private var enabled = true
    private var managed = false
    private var owned = false
    private var legacyCalls = 0
    private var prepares = 0
    private var failure: Exception? = null
    private val resolver =
        WindowPhoneAudioResolver(
            object : WearSyncedAudioResolver {
                override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> {
                    legacyCalls++
                    return Result.success("local.m4a")
                }
            },
            { enabled },
            { managed },
            { owned },
            {
                prepares++
                failure?.let { throw it }
                "logdate-watch-media://stream"
            },
        )

    @Test fun `flag off retains legacy behavior`() =
        runTest {
            enabled = false
            assertEquals("local.m4a", resolver.resolvePlayableUri(note).getOrThrow())
            assertEquals(0, prepares)
        }

    @Test fun `managed phone references cannot be mistaken for owned watch recordings`() =
        runTest {
            managed = true
            owned = true
            assertEquals("logdate-watch-media://stream", resolver.resolvePlayableUri(note).getOrThrow())
            assertEquals(0, legacyCalls)
            assertEquals("phone.m4a", note.mediaRef)
        }

    @Test fun `owned watch recordings remain locally playable`() =
        runTest {
            owned = true
            assertEquals("local.m4a", resolver.resolvePlayableUri(note).getOrThrow())
            assertEquals(0, prepares)
        }

    @Test fun `verified media failures cannot fall through to legacy downloads`() =
        runTest {
            failure = java.io.IOException("Phone away")
            assertTrue(resolver.resolvePlayableUri(note).isFailure)
            assertEquals(0, legacyCalls)
        }

    @Test fun `cancellation is propagated`() =
        runTest {
            failure = CancellationException("Stopped")
            assertFailsWith<CancellationException> { resolver.resolvePlayableUri(note) }
        }
}
