package app.logdate.client.database

import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseStartupMonitorTest {
    @Test
    fun `database is not ready before its key and retained contents have been checked`() {
        assertEquals(DatabaseStartupState.Initializing, DatabaseStartupMonitor().state.value)
    }
}
