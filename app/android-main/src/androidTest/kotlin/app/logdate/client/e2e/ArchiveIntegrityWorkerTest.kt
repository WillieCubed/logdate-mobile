package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveGenerator
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaFile
import app.logdate.client.domain.export.archive.ArchiveMediaInventoryEntry
import app.logdate.client.domain.export.archive.ArchiveOwner
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreResult
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.feature.core.restore.RestoreError
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreWorker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ArchiveIntegrityWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var originalBindings: RestoreWorkerTestBindings

    @Before
    fun setup() {
        originalBindings = RestoreWorkerTestBindings()
    }

    @After
    fun teardown() {
        originalBindings.restore()
    }

    @Test
    fun repackedMetadataIsRejectedBeforeRecoveryAdmission() =
        runTest {
            val files = integrityFiles()
            files["data/journals.json"] = "{\"journals\":[]} ".encodeToByteArray()
            val archive = writeArchive(files)
            val original = archive.readBytes()
            val useCase = mockk<RestoreUserDataUseCase>(relaxed = true)
            val launcher = RecordingRestoreLauncher()
            installRestoreWorkerTestBindings(useCase, mockk(relaxed = true), launcher)
            val recoveryId = Uuid.random().toString()
            try {
                val worker = worker(archive, recoveryId)
                val failure = assertIs<ListenableWorker.Result.Failure>(worker.doWork())
                assertEquals(RestoreError.INVALID_ARCHIVE.name, failure.outputData.getString(RestoreWorker.ERROR_KEY))
                assertEquals(
                    RestoreError.INVALID_ARCHIVE,
                    launcher.completedOutcomes
                        .filterIsInstance<RestoreOutcome.Failure>()
                        .single()
                        .error,
                )
                assertFalse(context.filesDir.resolve("archive_recovery/records/$recoveryId.json").exists())
                assertFalse(
                    context.filesDir
                        .resolve("archive_recovery/sources")
                        .listFiles()
                        .orEmpty()
                        .any { it.name.startsWith(recoveryId) },
                )
                assertContentEquals(original, archive.readBytes())
                coVerify(exactly = 0) {
                    useCase.restore(
                        archive = any(),
                        options = any(),
                        mediaImporter = anyNullable(),
                        onProgress = anyNullable(),
                        onPendingMedia = anyNullable(),
                        mediaRecoveryId = any(),
                        expectedNoteScope = anyNullable(),
                    )
                }
            } finally {
                archive.delete()
                removeRecoveryFixture(recoveryId)
            }
        }

    @Test
    fun changedOriginalNeverReachesDurableMediaSave() =
        runTest {
            verifyMediaAdmission("abd".encodeToByteArray(), false)
        }

    @Test
    fun verifiedOriginalReachesDurableMediaSaveWithExactBytes() =
        runTest {
            verifyMediaAdmission("abc".encodeToByteArray(), true)
        }

    private suspend fun verifyMediaAdmission(
        incoming: ByteArray,
        accepted: Boolean,
    ) {
        val archive = writeArchive(integrityFiles().apply { this[MEDIA_PATH] = incoming })
        val recoveryId = Uuid.random().toString()
        var imported: String? = null
        var staged: File? = null
        val mediaManager = mockk<MediaManager>(relaxed = true)
        coEvery { mediaManager.saveMediaFromFile(any(), any(), any()) } coAnswers {
            staged = File(firstArg<String>())
            assertContentEquals(incoming, assertNotNull(staged).readBytes())
            "content://synthetic/verified-original"
        }
        val useCase =
            mockk<RestoreUserDataUseCase> {
                coEvery {
                    restore(
                        archive = any(),
                        options = any(),
                        mediaImporter = anyNullable(),
                        onProgress = anyNullable(),
                        onPendingMedia = anyNullable(),
                        mediaRecoveryId = any(),
                        expectedNoteScope = anyNullable(),
                    )
                } coAnswers {
                    imported = thirdArg<MediaImporter>().importMedia(MEDIA_PATH)
                    emptyResult()
                }
            }
        installRestoreWorkerTestBindings(useCase, mediaManager, RecordingRestoreLauncher())
        val temporaryFiles =
            context.cacheDir
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith("logdate_media_") }
                .toSet()
        try {
            assertIs<ListenableWorker.Result.Success>(worker(archive, recoveryId).doWork())
            if (accepted) {
                assertEquals("content://synthetic/verified-original", imported)
                assertFalse(assertNotNull(staged).exists(), "Verified staging file must be cleaned after durable save")
            } else {
                assertNull(imported)
            }
            coVerify(exactly = if (accepted) 1 else 0) { mediaManager.saveMediaFromFile(any(), any(), any()) }
            assertEquals(
                temporaryFiles,
                context.cacheDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.name.startsWith("logdate_media_") }
                    .toSet(),
            )
        } finally {
            archive.delete()
            removeRecoveryFixture(recoveryId)
        }
    }

    private fun removeRecoveryFixture(recoveryId: String) {
        context.filesDir.resolve("archive_recovery/records/$recoveryId.json").delete()
        context.filesDir
            .resolve("archive_recovery/sources")
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith(recoveryId) }
            .forEach { it.delete() }
    }

    private fun worker(
        archive: File,
        recoveryId: String,
    ): RestoreWorker =
        TestListenableWorkerBuilder<RestoreWorker>(context)
            .setInputData(
                workDataOf(RestoreWorker.SOURCE_URI_KEY to Uri.fromFile(archive).toString(), RestoreWorker.RECOVERY_ID_KEY to recoveryId),
            ).build()

    private fun emptyResult() =
        RestoreResult(
            ExportMetadata(
                exportDate = Instant.fromEpochMilliseconds(0),
                userId = "synthetic",
                deviceId = "synthetic",
                appVersion = "1.0",
                stats = ExportStats(0, 0, 0, 0),
            ),
            0,
            0,
            0,
            0,
            0,
            emptyList(),
        )

    private fun integrityFiles(): MutableMap<String, ByteArray> {
        val expected = "abc".encodeToByteArray()
        val manifest =
            ArchiveManifest(
                exportedAt = Instant.fromEpochMilliseconds(0),
                exportTimeZone = "UTC",
                generator = ArchiveGenerator(version = "1.0"),
                owner = ArchiveOwner(),
                scope = ArchiveScope(complete = false),
                counts = ArchiveCounts(0, 0, 0, 1, 0, 0, false),
                contents =
                    listOf(
                        ArchiveContent(ArchiveRole.JOURNALS, ArchivePath.of("data/journals.json"), "application/json"),
                        ArchiveContent(ArchiveRole.MEDIA_INVENTORY, ArchivePath.of("data/media.json"), "application/json"),
                        ArchiveContent(ArchiveRole.CHECKSUMS, ArchivePath.of("SHA256SUMS"), "text/plain"),
                    ),
            )
        val inventory =
            ArchiveMediaFile(
                listOf(
                    ArchiveMediaInventoryEntry(
                        ArchivePath.of(MEDIA_PATH),
                        "application/octet-stream",
                        expected.size.toLong(),
                        expected.toByteString().sha256().hex(),
                    ),
                ),
            )
        val files =
            linkedMapOf(
                "manifest.json" to ArchiveJson.document.encodeToString(ArchiveManifest.serializer(), manifest).encodeToByteArray(),
                "data/journals.json" to "{\"journals\":[]}".encodeToByteArray(),
                "data/media.json" to ArchiveJson.document.encodeToString(ArchiveMediaFile.serializer(), inventory).encodeToByteArray(),
                MEDIA_PATH to expected,
            )
        files["SHA256SUMS"] =
            files.entries
                .sortedBy { it.key }
                .joinToString("") {
                    "${it.value.toByteString().sha256().hex()}  ${it.key}\n"
                }.encodeToByteArray()
        return files
    }

    private fun writeArchive(files: Map<String, ByteArray>): File =
        File.createTempFile("integrity-worker-", ".zip", context.cacheDir).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                files.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private companion object {
        const val MEDIA_PATH = "media/original.bin"
    }
}
