package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileDiagnosticStorageTest {
    @Test
    fun `startup and clear remove abandoned diagnostic writes without touching other files`() =
        runTest {
            val root = Files.createTempDirectory("logdate-diagnostics-test").toFile()
            try {
                val abandoned = File(root, "history-00000000-0000-4000-8000-000000000001.pending")
                val unrelated = File(root, "unrelated.pending").apply { writeText("operational-state") }
                abandoned.writeText("old sanitized event")
                val storage = FileDiagnosticStorage(Path(root.path)) { }
                storage.read()
                assertTrue(!abandoned.exists())
                abandoned.writeText("interrupted write")
                storage.clear()
                assertTrue(!abandoned.exists())
                assertEquals("operational-state", unrelated.readText())
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `private history survives process recreation and clearing leaves other storage alone`() =
        runTest {
            val root = Files.createTempDirectory("logdate-diagnostics-test").toFile()
            try {
                val operational = File(root, "operational-state").apply { writeText("pending-retry") }
                val protected = mutableListOf<String>()

                fun storage() = FileDiagnosticStorage(Path(File(root, "diagnostics").path)) { path -> protected += path.toString() }
                DiagnosticHistory(storage(), { 1000L }).append(SyncDiagnosticEvent(DiagnosticPhase.MEDIA, DiagnosticOutcome.FAILED))
                val restarted = DiagnosticHistory(storage(), { 2000L })
                assertEquals(
                    DiagnosticPhase.MEDIA,
                    restarted
                        .report()
                        .events
                        .single()
                        .phase,
                )
                assertTrue(protected.isNotEmpty())
                restarted.clear()
                assertTrue(DiagnosticHistory(storage(), { 3000L }).report().events.isEmpty())
                assertEquals("pending-retry", operational.readText())
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun `failed file protection cannot replace existing history`() =
        runTest {
            val root = Files.createTempDirectory("logdate-diagnostics-test").toFile()
            try {
                val storage = FileDiagnosticStorage(Path(root.path)) { }
                storage.write("existing")
                val failing = FileDiagnosticStorage(Path(root.path)) { error("private filesystem detail") }
                assertFailsWith<IllegalStateException> { failing.write("replacement") }
                assertEquals("existing", storage.read())
            } finally {
                root.deleteRecursively()
            }
        }
}
