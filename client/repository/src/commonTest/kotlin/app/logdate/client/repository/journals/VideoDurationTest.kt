package app.logdate.client.repository.journals

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests how a video note carries its length. */
class VideoDurationTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val json = Json { encodeDefaults = true }

    @Test
    fun `a video keeps its length through JSON`() {
        val video = JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "clip.mp4", durationMs = 61_250)

        val encoded = json.encodeToString(JournalNote.serializer(), video)

        assertTrue("\"durationMs\":61250" in encoded)
        assertEquals(video, json.decodeFromString(JournalNote.serializer(), encoded))
    }

    @Test
    fun `a video of unknown length encodes as it did before the field existed`() {
        val video = JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "clip.mp4")

        val encoded = json.encodeToString(JournalNote.serializer(), video)

        assertFalse("durationMs" in encoded)
        assertNull(json.decodeFromString(JournalNote.serializer(), encoded).let { (it as JournalNote.Video).durationMs })
    }
}
