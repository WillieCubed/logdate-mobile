package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.UnavailableMediaAsset
import app.logdate.client.domain.restore.UnavailableMediaReason
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ArchiveRecoveryStoreTest {
    @Test
    fun `replacement of unreadable metadata remains pending after process death`() {
        val root = Files.createTempDirectory("archive-metadata-replay-").toFile()
        try {
            val directory = root.resolve("recovery")
            val store = ArchiveRecoveryStore(directory, root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val id = Uuid.random().toString()
            val scope = ArchiveRecoveryScope("owner", "https://example.test")
            store.begin(id, scope, source, ImportOptions(), "Archive")
            store.finish(
                id,
                RestoreSummary(
                    "Archive",
                    journalsImported = 0,
                    notesImported = 0,
                    draftsImported = 0,
                    journalLinksImported = 0,
                    mediaImported = 0,
                    needsAnotherArchive = true,
                ),
            )
            store.replaceSource(id, source, "replacement")
            val restarted = ArchiveRecoveryStore(directory, root.resolve("cache"))
            assertFalse(requireNotNull(restarted.read(id)).metadataApplied)
            assertFalse(requireNotNull(restarted.read(id)).requiresSource)
            assertEquals(id, restarted.pending(scope).single().id)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cancellation before the source copy is published remains paused`() {
        val root = Files.createTempDirectory("archive-early-cancel-").toFile()
        try {
            val store = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val id = Uuid.random().toString()
            store.setPaused(id, true)
            store.begin(id, ArchiveRecoveryScope("owner", "https://example.test"), source, ImportOptions(), "Archive")
            assertTrue(requireNotNull(store.read(id)).pausedByUser)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `one damaged record cannot block healthy recovery and is retained`() {
        val root = Files.createTempDirectory("archive-record-isolation-").toFile()
        try {
            val directory = root.resolve("recovery")
            val store = ArchiveRecoveryStore(directory, root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val scope = ArchiveRecoveryScope("owner", "https://example.test")
            val id = Uuid.random().toString()
            store.begin(id, scope, source, ImportOptions(), "Healthy archive")
            val broken = directory.resolve("records/${Uuid.random()}.json").apply { writeText("{truncated") }
            assertEquals(listOf(id), store.pending(scope).map { it.id })
            assertTrue(broken.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `explicit pause survives checkpoints and restart until resumed`() {
        val root = Files.createTempDirectory("archive-pause-").toFile()
        try {
            val directory = root.resolve("recovery")
            val store = ArchiveRecoveryStore(directory, root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val scope = ArchiveRecoveryScope("owner", "https://example.test")
            val id = Uuid.random().toString()
            store.begin(id, scope, source, ImportOptions(), "Archive")
            store.setPaused(id, true)
            store.replacePending(id, emptyList())
            val restarted = ArchiveRecoveryStore(directory, root.resolve("cache"))
            assertTrue(requireNotNull(restarted.read(id)).pausedByUser)
            restarted.setPaused(id, false)
            assertFalse(requireNotNull(store.read(id)).pausedByUser)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `legacy records without timestamps cannot appear newer on every reconnect`() {
        val root = Files.createTempDirectory("archive-legacy-order-").toFile()
        try {
            val directory = root.resolve("recovery")
            val store = ArchiveRecoveryStore(directory, root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val scope = ArchiveRecoveryScope("owner", "https://example.test")
            val legacy = Uuid.random().toString()
            val latest = Uuid.random().toString()
            store.begin(legacy, scope, source, ImportOptions(), "Earlier archive")
            val file = directory.resolve("records/$legacy.json")
            val json = kotlinx.serialization.json.Json
            val document = json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
            file.writeText(
                kotlinx.serialization.json
                    .JsonObject(document.filterKeys { it != "createdAt" })
                    .toString(),
            )
            store.begin(latest, scope, source, ImportOptions(), "Current archive")
            assertEquals(0L, store.read(legacy)?.createdAt)
            assertEquals(latest, store.pending(scope).last().id)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `source replacement atomically changes its generation and invalidates old candidates`() {
        val root = Files.createTempDirectory("archive-source-generation-").toFile()
        try {
            val store = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("cache"))
            val source = root.resolve("archive.zip").apply { writeBytes(byteArrayOf(1)) }
            val id = Uuid.random().toString()
            store.begin(id, ArchiveRecoveryScope("owner", "https://example.test"), source, ImportOptions(), "Archive")
            val previous = store.source(id)
            store.recordPending(
                id,
                UnavailableMediaAsset(
                    Uuid.random().toString(),
                    reference = "missing",
                    archivePath = "old/file.jpg",
                    reason = UnavailableMediaReason.IMPORT_FAILED,
                    recoveredReference = "file:///candidate-a.jpg",
                ),
            )
            source.writeBytes(byteArrayOf(2))
            store.replaceSource(id, source, "replacement-work")
            val restarted = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("other-cache"))
            assertFalse(previous.exists())
            assertEquals("replacement-work", restarted.read(id)?.sourceWorkId)
            assertContentEquals(byteArrayOf(2), restarted.source(id).readBytes())
            val pending = requireNotNull(restarted.read(id)).pending.single()
            assertEquals(null, pending.archivePath)
            assertEquals(null, pending.recoveredReference)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `source and pending attachment survive a new store instance without crossing accounts`() {
        val root = Files.createTempDirectory("archive-recovery-").toFile()
        try {
            val id = Uuid.random().toString()
            val scope = ArchiveRecoveryScope("local-owner", "https://example.test")
            val archive = root.resolve("chosen.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val store = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("cache"))
            store.begin(id, scope, archive, ImportOptions(), "Journal archive.zip")
            val asset =
                UnavailableMediaAsset(
                    Uuid.random().toString(),
                    reference = "logdate-unavailable://archive/original",
                    archivePath = "media/original.jpg",
                    reason = UnavailableMediaReason.IMPORT_FAILED,
                )
            store.recordPending(id, asset)
            archive.delete()
            val restarted = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("other-cache"))
            assertContentEquals(byteArrayOf(1, 2, 3), restarted.source(id).readBytes())
            assertEquals(listOf(asset), restarted.pending(scope).single().pending)
            assertTrue(restarted.pending(scope.copy(ownerId = "different-owner")).isEmpty())
            assertTrue(restarted.pending(scope.copy(serverOrigin = "https://other.test")).isEmpty())
            val summary =
                RestoreSummary(
                    source = "Journal archive.zip",
                    journalsImported = 0,
                    notesImported = 1,
                    draftsImported = 0,
                    journalLinksImported = 0,
                    mediaImported = 0,
                    unavailableOriginals = 1,
                    recoveryId = id,
                )
            restarted.finish(id, summary)
            assertEquals(summary, store.read(id)?.summary)
            assertTrue(store.source(id).isFile)
            restarted.replacePending(id, emptyList())
            restarted.finish(id, summary.copy(unavailableOriginals = 0))
            assertFalse(store.source(id).exists())
            assertTrue(store.pending(scope).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed source copy cannot publish a recovery record or accept a path as an id`() {
        val root = Files.createTempDirectory("archive-recovery-failure-").toFile()
        try {
            val store = ArchiveRecoveryStore(root.resolve("recovery"), root.resolve("cache"))
            val id = Uuid.random().toString()
            assertFailsWith<java.io.IOException> {
                store.begin(
                    id,
                    ArchiveRecoveryScope("owner", "https://example.test"),
                    root.resolve("missing.zip"),
                    ImportOptions(),
                    "Archive",
                )
            }
            assertEquals(null, store.read(id))
            assertFalse(store.source(id).exists())
            assertFailsWith<IllegalArgumentException> { store.source("../../other") }
            assertTrue(
                root
                    .resolve("cache")
                    .listFiles()
                    .orEmpty()
                    .isEmpty(),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
