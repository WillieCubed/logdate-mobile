package app.logdate.client.sync.datalayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WearAudioRequestPathsTest {
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    @Test
    fun `audio transfer path uses note id`() {
        assertEquals(
            "/logdate/notes/550e8400-e29b-41d4-a716-446655440000/audio",
            WearAudioRequestPaths.audioTransferPath(noteId),
        )
    }

    @Test
    fun `audio request path uses note id`() {
        assertEquals(
            "/logdate/notes/550e8400-e29b-41d4-a716-446655440000/audio/request",
            WearAudioRequestPaths.audioRequestPath(noteId),
        )
    }

    @Test
    fun `request path detection only matches audio request paths`() {
        assertTrue(WearAudioRequestPaths.isAudioRequestPath(WearAudioRequestPaths.audioRequestPath(noteId)))
        assertFalse(WearAudioRequestPaths.isAudioRequestPath(WearAudioRequestPaths.audioTransferPath(noteId)))
        assertFalse(WearAudioRequestPaths.isAudioRequestPath("/logdate/sync/request"))
    }

    @Test
    fun `note id can be parsed from audio request path`() {
        assertEquals(noteId, WearAudioRequestPaths.noteIdFromAudioRequestPath(WearAudioRequestPaths.audioRequestPath(noteId)))
    }
}
