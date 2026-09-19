package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.ExportError
import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.ArchiveLeakScanner
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.IOException
import okio.Source
import okio.Timeout
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExportArchiveUseCaseTest : ArchiveExportFixture() {
    private suspend fun export(
        options: ArchiveExportOptions = ArchiveExportOptions(),
        opener: MediaSourceOpener = readable,
    ): Pair<InMemoryArchiveContainer, List<ArchiveExportProgress>> {
        val container = InMemoryArchiveContainer()
        val progress = useCase(opener).export(options, container).toList()
        return container to progress
    }

    private fun InMemoryArchiveContainer.notes() =
        ArchiveJson.document.decodeFromString(ArchiveNoteFile.serializer(), text("data/notes.json")).notes

    private fun InMemoryArchiveContainer.manifest() =
        ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), text("manifest.json"))

    @Test
    fun `an export completes and reports what it wrote`() =
        runTest {
            val (_, progress) = export()

            assertEquals(ArchiveExportProgress.Starting, progress.first())
            val completed = assertIs<ArchiveExportProgress.Completed>(progress.last())
            assertEquals(2, completed.summary.counts.journals)
            assertEquals(4, completed.summary.counts.notes)
        }

    @Test
    fun `media is written before the files that describe it and the checksums come last`() =
        runTest {
            val (container, _) = export()

            val paths = container.paths
            val readme = paths.indexOf("README.txt")
            assertTrue(readme > 0 && paths.take(readme).all { it.startsWith("media/") }, "$paths")
            assertEquals("manifest.json", paths[readme + 1])
            assertTrue(paths.indexOf("data/media.json") < paths.indexOf("SHA256SUMS"))
            assertEquals("SHA256SUMS", paths.last())
        }

    @Test
    fun `the checksums match the bytes of every other file`() =
        runTest {
            val (container, _) = export()

            val listed = assertNotNull(ChecksumFile.parse(container.text("SHA256SUMS")))
            val everythingElse = container.paths - "SHA256SUMS"
            assertEquals(everythingElse.toSet(), listed.keys)
            everythingElse.forEach { path ->
                assertEquals(
                    container
                        .bytes(path)
                        .toByteString()
                        .sha256()
                        .hex(),
                    listed.getValue(path),
                    path,
                )
            }
        }

    @Test
    fun `no device path or content uri appears in any file`() =
        runTest {
            val (container, _) = export()
            val giveaways =
                listOf(
                    "1000025292",
                    "recording_abc",
                    "app.logdate",
                    "/var/mobile",
                    "content://",
                    "file://",
                    "/data/user",
                    "media/55",
                    "media/99",
                    "media/77",
                )

            container.textFiles().forEach { (path, text) ->
                giveaways.forEach { assertTrue(it !in text, "$path leaks '$it'") }
                val structural =
                    when {
                        path.endsWith(".jsonl") -> ArchiveLeakScanner.findInJsonLines(text)
                        path.endsWith(".json") -> ArchiveLeakScanner.findInJson(text)
                        else -> emptyList()
                    }
                assertEquals(emptyList(), structural, path)
            }
        }

    @Test
    fun `every media path an entry points at is in the archive and unique ignoring case`() =
        runTest {
            val (container, _) = export()
            val referenced =
                container.notes().mapNotNull { it.media?.path?.value } +
                    ArchiveJson.document
                        .decodeFromString(ArchiveDraftFile.serializer(), container.text("data/drafts.json"))
                        .drafts
                        .flatMap { it.blocks }
                        .mapNotNull { it.media?.path?.value }

            referenced.forEach { assertTrue(container.has(it), "$it is referenced but not in the archive") }
            assertEquals(
                container.paths.size,
                container.paths
                    .map { it.lowercase() }
                    .toSet()
                    .size,
                "paths collide when case is ignored",
            )
            container.paths.forEach { assertNotNull(ArchivePath.parse(it), "$it is not a valid archive path") }
        }

    @Test
    fun `the manifest media count and inventory match the media files written`() =
        runTest {
            val (container, _) = export()

            val written = container.paths.filter { it.startsWith("media/") }
            val inventory = ArchiveJson.document.decodeFromString(ArchiveMediaFile.serializer(), container.text("data/media.json")).files
            assertEquals(3, written.size)
            assertEquals(written.toSet(), inventory.map { it.path.value }.toSet())
            assertEquals(written.size, container.manifest().counts.media)
            inventory.forEach { assertEquals(container.bytes(it.path.value).size.toLong(), it.bytes) }
        }

    @Test
    fun `media is named by capture time in the zone the note was captured in`() =
        runTest {
            val (container, _) = export()

            val byId = container.notes().associateBy { it.id }
            // The image note has no recorded zone, so it is named in the export zone (Denver, UTC-6).
            assertEquals(
                "media/photos/2026/2026-09-17_21-30-05.jpg",
                byId
                    .getValue(imageNote.uid.toString())
                    .media
                    ?.path
                    ?.value,
            )
            assertEquals(
                "media/audio/2026/2026-09-19_04-00-00.m4a",
                byId
                    .getValue(audioNote.uid.toString())
                    .media
                    ?.path
                    ?.value,
            )
        }

    @Test
    fun `a note records its zone and its local time only when the zone is known`() =
        runTest {
            val (container, _) = export()

            val byId = container.notes().associateBy { it.id }
            val text = byId.getValue(textNote.uid.toString())
            assertEquals("Asia/Tokyo", text.timeZone)
            assertEquals("2026-09-18T12:30:05+09:00", text.createdAtLocal)
            val image = byId.getValue(imageNote.uid.toString())
            assertEquals(null, image.timeZone)
            assertEquals(null, image.createdAtLocal)
        }

    @Test
    fun `text is stored exactly as written and labelled as markdown`() =
        runTest {
            val (container, _) = export()

            val text = container.notes().first { it.id == textNote.uid.toString() }
            assertEquals("Walk by the *river* http://example.com", text.text)
            assertEquals(ArchiveTextFormat.MARKDOWN, text.textFormat)
        }

    @Test
    fun `a note lists every journal it is in and an unfiled note lists none`() =
        runTest {
            val (container, _) = export()

            val byId = container.notes().associateBy { it.id }
            assertEquals(setOf(daily.id.toString(), travel.id.toString()), byId.getValue(textNote.uid.toString()).journalIds.toSet())
            assertEquals(listOf(daily.id.toString()), byId.getValue(imageNote.uid.toString()).journalIds)
            assertEquals(emptyList(), byId.getValue(audioNote.uid.toString()).journalIds)
        }

    @Test
    fun `an unreadable media file is omitted with a reason and leaves no dangling path`() =
        runTest {
            val (container, _) = export()

            val video = container.notes().first { it.id == videoNote.uid.toString() }
            assertEquals(ArchiveMediaStatus.OMITTED, video.media?.status)
            assertEquals(ArchiveOmissionReason.UNREADABLE, video.media?.omittedReason)
            assertEquals(null, video.media?.path)
            assertTrue(
                container.manifest().scope.omitted.any {
                    it.category == ArchiveCategory.MEDIA &&
                        it.reason == ArchiveOmissionReason.UNREADABLE
                },
            )
        }

    @Test
    fun `a file that cannot be opened when it is copied is omitted and the export still completes`() =
        runTest {
            val (container, progress) = export(opener = UnavailableWhenCopied(readable, imageReference, failure = null))

            assertIs<ArchiveExportProgress.Completed>(progress.last())
            assertImageOmittedEverywhere(container)
        }

    @Test
    fun `a file that fails to open when it is copied is omitted and the export still completes`() =
        runTest {
            val (container, progress) =
                export(opener = UnavailableWhenCopied(readable, imageReference, failure = { IOException("cloud file evicted") }))

            assertIs<ArchiveExportProgress.Completed>(progress.last())
            assertImageOmittedEverywhere(container)
        }

    private fun assertImageOmittedEverywhere(container: InMemoryArchiveContainer) {
        val image = container.notes().first { it.id == imageNote.uid.toString() }
        assertEquals(ArchiveMediaStatus.OMITTED, image.media?.status)
        assertEquals(ArchiveOmissionReason.UNREADABLE, image.media?.omittedReason)
        assertEquals(null, image.media?.path)
        val written = container.paths.filter { it.startsWith("media/") }
        assertEquals(2, written.size)
        val inventory = ArchiveJson.document.decodeFromString(ArchiveMediaFile.serializer(), container.text("data/media.json")).files
        assertEquals(written.toSet(), inventory.map { it.path.value }.toSet())
        assertEquals(written.size, container.manifest().counts.media)
        assertTrue(
            container.manifest().scope.omitted.any {
                it.category == ArchiveCategory.MEDIA &&
                    it.reason == ArchiveOmissionReason.UNREADABLE
            },
        )
        assertEquals((container.paths - "SHA256SUMS").toSet(), assertNotNull(ChecksumFile.parse(container.text("SHA256SUMS"))).keys)
    }

    @Test
    fun `every source the export opens is closed`() =
        runTest {
            val tracking = ClosureTrackingOpener(readable)

            export(opener = tracking)

            assertTrue(tracking.sources.isNotEmpty())
            assertEquals(0, tracking.sources.count { !it.closed }, "sources left open")
        }

    @Test
    fun `the archive is built on the dispatcher meant for blocking work`() =
        runTest {
            val blocking = StandardTestDispatcher(testScheduler, name = "blocking")
            var openedOn: ContinuationInterceptor? = null
            val opener =
                MediaSourceOpener { reference ->
                    openedOn = currentCoroutineContext()[ContinuationInterceptor]
                    readable.open(reference)
                }

            useCase(opener, ioDispatcher = blocking).export(ArchiveExportOptions(), InMemoryArchiveContainer()).toList()

            assertSame(blocking, openedOn)
        }

    @Test
    fun `leaving media out marks every media reference as not requested and writes no media`() =
        runTest {
            val (container, _) = export(ArchiveExportOptions(includeMedia = false))

            assertTrue(container.paths.none { it.startsWith("media/") })
            assertTrue(!container.has("data/media.json"))
            val media = container.notes().mapNotNull { it.media }
            assertTrue(
                media.isNotEmpty() &&
                    media.all { it.status == ArchiveMediaStatus.OMITTED && it.omittedReason == ArchiveOmissionReason.NOT_REQUESTED },
            )
            assertTrue(readable.opened.isEmpty(), "no file is opened when media was not requested")
        }

    @Test
    fun `a date range includes only entries inside it and the end is inclusive`() =
        runTest {
            val options = ArchiveExportOptions(from = instantOf("2026-09-19T00:00:00Z"), to = instantOf("2026-09-19T10:00:00Z"))

            val (container, _) = export(options)

            assertEquals(listOf(audioNote.uid.toString()), container.notes().map { it.id })
            val scope = container.manifest().scope
            assertEquals(false, scope.complete)
            assertEquals(options.from, scope.dateRange?.from)
            assertEquals(options.to, scope.dateRange?.to)
        }

    @Test
    fun `the manifest says the export is not complete and lists what is missing`() =
        runTest {
            val (container, _) = export()

            val scope = container.manifest().scope
            assertEquals(false, scope.complete)
            assertTrue(ArchiveOmission(ArchiveCategory.PEOPLE, ArchiveOmissionReason.NOT_YET_SUPPORTED) in scope.omitted)
            assertTrue(ArchiveOmission(ArchiveCategory.EDITOR_DRAFTS, ArchiveOmissionReason.NOT_YET_SUPPORTED) in scope.omitted)
        }

    @Test
    fun `sync internals and device ids are not exported`() =
        runTest {
            val (container, _) = export()

            val samples = container.text("data/location-history.jsonl").lines().filter { it.isNotBlank() }
            assertEquals(1, samples.size)
            listOf("user-1", "device-1", "sample-1", "syncVersion", "deviceId", "userId").forEach { token ->
                container.textFiles().forEach { (path, text) -> assertTrue(token !in text, "$path contains $token") }
            }
        }

    @Test
    fun `a profile without timestamps the app set does not write a year 100001 date`() =
        runTest {
            val (container, _) = export()

            val profile = container.text("data/profile.json")
            assertTrue("DISTANT" !in profile && "-100001" !in profile, profile)
            assertTrue("\"displayName\": \"Sam\"" in profile)
        }

    @Test
    fun `the same data and clock give byte identical archives`() =
        runTest {
            val (first, _) = export()
            val (second, _) = export(opener = Files(mapOf(imageReference to jpeg, audioReference to m4a, draftReference to jpeg)))

            assertEquals(first.text("SHA256SUMS"), second.text("SHA256SUMS"))
        }

    @Test
    fun `the readme states what the manifest states`() =
        runTest {
            val (container, _) = export()

            val readme = container.text("README.txt")
            assertTrue("2 journals" in readme)
            assertTrue("4 entries" in readme)
            assertTrue("people: this version of LogDate does not export it yet" in readme.split(Regex("\\s+")).joinToString(" "))
        }

    @Test
    fun `a failure while writing is reported as failed and not thrown`() =
        runTest {
            val broken =
                object : ArchiveContainer {
                    override fun entry(
                        path: ArchivePath,
                        compress: Boolean,
                        write: (okio.Sink) -> Unit,
                    ) = throw IllegalStateException("disk full")
                }

            val progress = useCase().export(ArchiveExportOptions(), broken).toList()

            assertEquals(ArchiveExportProgress.Failed(ExportError.UNKNOWN), progress.last())
        }

    /** Hands out a file while the export is planned, then reports it gone when the copy comes back for it. */
    private class UnavailableWhenCopied(
        private val delegate: MediaSourceOpener,
        private val target: String,
        private val failure: (() -> Throwable)?,
    ) : MediaSourceOpener {
        private var opens = 0

        override suspend fun open(reference: String): Source? {
            if (reference == target && ++opens > 1) {
                failure?.let { throw it() }
                return null
            }
            return delegate.open(reference)
        }
    }

    private class ClosureTrackingOpener(
        private val delegate: MediaSourceOpener,
    ) : MediaSourceOpener {
        val sources = mutableListOf<TrackedSource>()

        override suspend fun open(reference: String): Source? = delegate.open(reference)?.let { TrackedSource(it).also(sources::add) }
    }

    private class TrackedSource(
        private val delegate: Source,
    ) : Source {
        var closed = false
            private set

        override fun read(
            sink: Buffer,
            byteCount: Long,
        ): Long = delegate.read(sink, byteCount)

        override fun timeout(): Timeout = delegate.timeout()

        override fun close() {
            closed = true
            delegate.close()
        }
    }
}
