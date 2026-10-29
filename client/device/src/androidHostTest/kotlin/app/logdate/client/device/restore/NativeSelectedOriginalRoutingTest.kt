package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeSelectedOriginalRoutingTest {
    @Test
    fun `framework root directory callbacks never require a child destination mapping`() {
        assertFalse(
            retainSelectedNativeOriginal(BackupAgent.TYPE_DIRECTORY, {
                error("Root has no child backup domain")
            }, { error("Directory must not enter media custody") }),
        )
    }

    @Test
    fun `selected original bytes enter custody rather than the default XML restore filter`() {
        val retained = mutableListOf<String>()
        assertTrue(retainSelectedNativeOriginal(BackupAgent.TYPE_FILE, { "files/audio_notes/take.m4a" }, retained::add))
        assertEquals(listOf("files/audio_notes/take.m4a"), retained)
    }

    @Test
    fun `journal and recovery files retain their original callback path`() {
        for (path in listOf("database/logdate", "files/native_journal_transfer/current/database/logdate", "files/logdate.preferences_pb")) {
            assertFalse(retainSelectedNativeOriginal(BackupAgent.TYPE_FILE, { path }, { error("Not a selected original") }))
        }
    }
}
