package app.logdate.feature.core.restore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestoreWorkSummaryTest {
    @Test
    fun `unreadable entries retain an actionable source request after work data is compacted`() {
        val summary =
            RestoreSummary(
                "Archive",
                journalsImported = 0,
                notesImported = 0,
                draftsImported = 0,
                journalLinksImported = 0,
                mediaImported = 0,
                warnings = List(5) { "Kept a newer edit" } + "Skipped unsupported note type: future",
            )
        assertTrue(summary.needsAnotherArchive)
        assertTrue(summary.forWorkData().needsAnotherArchive)
    }

    @Test
    fun `large archive labels and warnings fit below the WorkManager data limit`() {
        val text = "\u0001😀".repeat(10_000)
        val summary =
            RestoreSummary(
                source = text,
                appVersion = text,
                deviceId = text,
                journalsImported = 1,
                notesImported = 2,
                draftsImported = 3,
                journalLinksImported = 4,
                mediaImported = 5,
                warnings = List(100) { text },
                unavailableOriginals = 17,
                unavailablePreviews = 6,
                recoveryId = "recovery",
            )
        val compact = summary.forWorkData()
        assertTrue(Json.encodeToString(compact).encodeToByteArray().size < 8_000)
        assertEquals(17, compact.unavailableOriginals)
        assertEquals(6, compact.unavailablePreviews)
        assertEquals(summary.recoveryId, compact.recoveryId)
        assertEquals(100, summary.warnings.size)
    }
}
