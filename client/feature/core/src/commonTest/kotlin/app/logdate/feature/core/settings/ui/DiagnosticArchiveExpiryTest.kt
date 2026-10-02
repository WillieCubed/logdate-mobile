package app.logdate.feature.core.settings.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticArchiveExpiryTest {
    @Test
    fun `activation sweep expires only private diagnostic zip names after thirty minutes`() {
        val now = 2 * DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS
        assertEquals(
            listOf("report-old.zip", "report-boundary.zip"),
            expiredDiagnosticArchives(
                now,
                listOf(
                    PrivateDiagnosticArchive("report-old.zip", 0),
                    PrivateDiagnosticArchive("report-boundary.zip", now - DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS),
                    PrivateDiagnosticArchive("report-fresh.zip", now - DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS + 1),
                    PrivateDiagnosticArchive("other.zip", 0),
                    PrivateDiagnosticArchive("report-../escape.zip", 0),
                    PrivateDiagnosticArchive("report-no-extension", 0),
                ),
            ),
        )
    }

    @Test
    fun `timer delay cannot extend a report past thirty minutes`() {
        val created = 1_000L
        assertEquals(DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS, diagnosticArchiveExpiryDelay(created, created))
        assertEquals(1L, diagnosticArchiveExpiryDelay(created + DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS - 1, created))
        assertEquals(0L, diagnosticArchiveExpiryDelay(created + DIAGNOSTIC_ARCHIVE_EXPIRY_MILLIS, created))
    }
}
