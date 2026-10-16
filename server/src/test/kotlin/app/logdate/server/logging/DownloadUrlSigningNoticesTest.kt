package app.logdate.server.logging

import io.github.aakira.napier.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadUrlSigningNoticesTest {
    @Test
    fun `signing failures keep their own codes in production logs`() {
        assertEquals(
            "SERVER_MEDIA_URL_SIGNING_FAILED",
            safeServerMessage(LogLevel.ERROR, tag = null, message = DownloadUrlSigningNotices.MEDIA_SIGNING_FAILED),
        )
        assertEquals(
            "SERVER_BACKUP_URL_SIGNING_FAILED",
            safeServerMessage(LogLevel.ERROR, tag = null, message = DownloadUrlSigningNotices.BACKUP_SIGNING_FAILED),
        )
    }
}
