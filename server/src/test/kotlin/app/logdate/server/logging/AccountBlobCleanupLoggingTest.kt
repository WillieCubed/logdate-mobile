package app.logdate.server.logging

import io.github.aakira.napier.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBlobCleanupLoggingTest {
    @Test
    fun `cleanup overdue signal survives the private log boundary`() {
        assertEquals(
            "SERVER_ACCOUNT_BLOB_CLEANUP_OVERDUE",
            safeServerMessage(LogLevel.ERROR, null, "Account blob cleanup queue overdue"),
        )
    }
}
