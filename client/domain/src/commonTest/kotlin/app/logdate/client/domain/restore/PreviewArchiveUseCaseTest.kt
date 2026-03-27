package app.logdate.client.domain.restore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewArchiveUseCaseTest {
    private val useCase = PreviewArchiveUseCase()

    @Test
    fun `valid metadata produces correct preview`() {
        val metadataJson =
            """
            {
                "version": "1.0",
                "exportDate": "2026-03-20T10:30:00Z",
                "userId": "user-123",
                "deviceId": "device-456",
                "appVersion": "2.1.0",
                "stats": {
                    "journalCount": 5,
                    "noteCount": 42,
                    "draftCount": 3,
                    "mediaCount": 15
                }
            }
            """.trimIndent()

        val preview = useCase.preview(metadataJson)

        assertEquals("2.1.0", preview.appVersion)
        assertEquals(5, preview.stats.journalCount)
        assertEquals(42, preview.stats.noteCount)
        assertEquals(3, preview.stats.draftCount)
        assertEquals(15, preview.stats.mediaCount)
        assertTrue(preview.hasDrafts)
        assertTrue(preview.hasMedia)
    }

    @Test
    fun `zero drafts and media reports hasDrafts and hasMedia as false`() {
        val metadataJson =
            """
            {
                "version": "1.0",
                "exportDate": "2026-03-20T10:30:00Z",
                "userId": "user-123",
                "deviceId": "device-456",
                "appVersion": "2.0.0",
                "stats": {
                    "journalCount": 2,
                    "noteCount": 10,
                    "draftCount": 0,
                    "mediaCount": 0
                }
            }
            """.trimIndent()

        val preview = useCase.preview(metadataJson)

        assertFalse(preview.hasDrafts)
        assertFalse(preview.hasMedia)
    }

    @Test
    fun `unknown fields in metadata are ignored`() {
        val metadataJson =
            """
            {
                "version": "1.0",
                "exportDate": "2026-01-15T08:00:00Z",
                "userId": "user-123",
                "deviceId": "device-456",
                "appVersion": "1.5.0",
                "unknownField": "should be ignored",
                "stats": {
                    "journalCount": 1,
                    "noteCount": 5,
                    "draftCount": 0,
                    "mediaCount": 0,
                    "extraStat": 99
                }
            }
            """.trimIndent()

        val preview = useCase.preview(metadataJson)

        assertEquals("1.5.0", preview.appVersion)
        assertEquals(1, preview.stats.journalCount)
    }

    @Test
    fun `corrupt JSON throws exception`() {
        assertFailsWith<Exception> {
            useCase.preview("not valid json {{{")
        }
    }
}
