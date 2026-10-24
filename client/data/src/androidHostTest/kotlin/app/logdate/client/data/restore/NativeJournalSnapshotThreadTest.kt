package app.logdate.client.data.restore

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class NativeJournalSnapshotThreadTest {
    @Test
    fun `DataStore worker completes before the SQL scope is written on its owner thread`() {
        val owner = Thread.currentThread()
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val events = mutableListOf<String>()
        try {
            val captured =
                capturePreferencesOnSqlThread(
                    capture = {
                        withContext(worker) {
                            assertNotSame(owner, Thread.currentThread())
                            events += "preferences"
                            "pinned-preferences"
                        }
                    },
                    write = { preferences ->
                        assertSame(owner, Thread.currentThread())
                        assertEquals("pinned-preferences", preferences)
                        events += "sql-scope"
                    },
                )

            assertEquals("pinned-preferences", captured)
            assertEquals(listOf("preferences", "sql-scope"), events)
        } finally {
            worker.close()
        }
    }

    @Test
    fun `failed preference capture cannot publish a SQL scope`() {
        var wroteScope = false

        assertFailsWith<IllegalStateException> {
            capturePreferencesOnSqlThread<String>(
                capture = { throw IllegalStateException("Preference capture failed") },
                write = { wroteScope = true },
            )
        }

        assertEquals(false, wroteScope)
    }
}
