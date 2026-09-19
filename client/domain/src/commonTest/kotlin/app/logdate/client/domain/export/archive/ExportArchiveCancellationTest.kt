package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportArchiveCancellationTest : ArchiveExportFixture() {
    private fun finished(emissions: List<ArchiveExportProgress>) =
        emissions.any {
            it is ArchiveExportProgress.Completed ||
                it is ArchiveExportProgress.Failed
        }

    @Test
    fun `cancelling while media is being resolved stops the export before anything is written`() =
        runTest {
            val opening = CompletableDeferred<Unit>()
            val hanging =
                MediaSourceOpener {
                    opening.complete(Unit)
                    awaitCancellation()
                }
            val container = InMemoryArchiveContainer()
            val emissions = mutableListOf<ArchiveExportProgress>()

            val export = launch { useCase(hanging).export(ArchiveExportOptions(), container).collect { emissions += it } }
            opening.await()
            export.cancelAndJoin()

            assertTrue(!finished(emissions), "cancellation is neither a completion nor a failure: $emissions")
            assertTrue(container.paths.isEmpty(), "nothing is written before media is resolved: ${container.paths}")
        }

    @Test
    fun `cancelling while a file is being copied stops before the archive is finished`() =
        runTest {
            lateinit var export: Job
            val opened = mutableMapOf<String, Int>()
            val interrupted = mutableListOf<CancelsOnFirstRead>()
            val cancellingOnCopy =
                MediaSourceOpener { reference ->
                    val count = (opened[reference] ?: 0) + 1
                    opened[reference] = count
                    if (count == 1) Buffer().write(jpeg) else CancelsOnFirstRead { export }.also(interrupted::add)
                }
            val container = InMemoryArchiveContainer()
            val emissions = mutableListOf<ArchiveExportProgress>()

            export = launch { useCase(cancellingOnCopy).export(ArchiveExportOptions(), container).collect { emissions += it } }
            export.join()

            assertTrue(!finished(emissions), "cancellation is neither a completion nor a failure: $emissions")
            assertTrue("SHA256SUMS" !in container.paths, "an interrupted export must not look finished")
            assertTrue(interrupted.isNotEmpty() && interrupted.all { it.closed }, "the interrupted source must be closed")
        }

    private class CancelsOnFirstRead(
        private val job: () -> Job,
    ) : Source {
        private var reads = 0

        var closed = false
            private set

        override fun read(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            if (reads++ == 0) job().cancel()
            sink.write(ByteArray(1024))
            return 1024
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
        }
    }
}
