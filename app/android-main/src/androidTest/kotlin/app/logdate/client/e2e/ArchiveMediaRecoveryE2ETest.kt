package app.logdate.client.e2e

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.ArchiveMediaRecoveryWorker
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** Run seed, repair, and verify in separate instrumentation processes, with force-stop between them. */
@RunWith(AndroidJUnit4::class)
class ArchiveMediaRecoveryE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture get(): File {
        val label = InstrumentationRegistry.getArguments().getString("archiveRecoveryFixtureLabel")
        require(label == null || label.matches(Regex("[a-z0-9-]+")))
        return context.noBackupFilesDir.resolve("archive-media-recovery-${label ?: "acceptance"}.json")
    }
    private val koin get() = GlobalContext.get()
    private val notes get() = koin.get<JournalNotesRepository>()
    private val journals get() = koin.get<JournalRepository>()
    private val links get() = koin.get<JournalContentRepository>()
    private val media get() = koin.get<MediaManager>()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun archiveRecoveryAcrossProcessRestart(): Unit =
        runBlocking {
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
            val arguments = InstrumentationRegistry.getArguments()
            arguments.getString("archiveRecoveryFixture")?.let {
                assertFalse(fixture.exists(), "Transferred test IDs must not replace an existing acceptance fixture")
                fixture.writeText(it)
            }
            val mode = arguments.getString("archiveRecoveryMode")
            org.junit.Assume.assumeTrue(
                "Archive recovery runs through its multi-process acceptance harness",
                mode in
                    setOf(
                        "seed",
                        "source-before-backup",
                        "repair",
                        "automatic",
                        "waiting",
                        "verify",
                        "cleanup-rejected-fixture",
                        "cancel-running",
                        "paused",
                        "resume-cancelled",
                        "verify-cancelled",
                    ),
            )
            if (mode in setOf("cancel-running", "paused", "resume-cancelled", "verify-cancelled")) {
                require(arguments.getString("archiveCancellationAcceptance") == "true")
            }
            when (mode) {
                "seed" -> {
                    seed(recoverableSource = arguments.getString("archiveCancellationAcceptance") == "true")
                }

                "source-before-backup" -> {
                    val state = JSONObject(fixture.readText())
                    val manager = WorkManager.getInstance(context)
                    kotlinx.coroutines.withTimeout(30_000) {
                        while (manager
                                .getWorkInfosForUniqueWork("archive_media_recovery_${state.getString("recoveryId")}")
                                .get()
                                .any { !it.state.isFinished }
                        ) {
                            kotlinx.coroutines.delay(100)
                        }
                    }
                    verifyRetainedWaitingState()
                }

                "repair" -> {
                    repair()
                }

                "automatic" -> {
                    automaticRepair()
                }

                "waiting" -> {
                    verifyRetainedWaitingState()
                }

                "verify" -> {
                    verify()
                }

                "cleanup-rejected-fixture" -> {
                    cleanupRejectedFixture()
                }

                "cancel-running" -> {
                    cancelRunningRecovery()
                }

                "paused" -> {
                    verifyCancelledAfterRestart()
                }

                "resume-cancelled" -> {
                    resumeCancelledRecovery()
                }

                "verify-cancelled" -> {
                    verifyCancelledCompletion()
                    verify()
                }

                else -> {
                    error("Choose seed, repair, or verify for the owned emulator")
                }
            }
        }

    /** Controlled save latency leaves the real worker at an accepted, persisted media checkpoint. */
    private suspend fun cancelRunningRecovery() {
        val state = JSONObject(fixture.readText())
        verifyEdits(state)
        val launcher = assertIs<AndroidRestoreLauncher>(koin.get<RestoreLauncher>())
        val manager = WorkManager.getInstance(context)
        val bindings = RestoreWorkerTestBindings()
        val delegate = media
        val reachedSecondOriginal = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val gated =
            object : MediaManager by delegate {
                override suspend fun saveMediaFromFile(
                    sourceFilePath: String,
                    fileName: String,
                    mimeType: String,
                ): String {
                    if (fileName == "replacement-video.mp4") {
                        reachedSecondOriginal.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancellationObserved.complete(Unit)
                        }
                    }
                    return delegate.saveMediaFromFile(sourceFilePath, fileName, mimeType)
                }
            }
        val workName = "archive_media_recovery_${state.getString("recoveryId")}"
        val previousWork =
            manager
                .getWorkInfosForUniqueWork(workName)
                .get()
                .map { it.id }
                .toSet()
        val pickerLaunches = trapDocumentPicker(launcher)
        var workId: UUID? = null
        var cancellationCompleted = false
        try {
            loadKoinModules(module { single<MediaManager> { gated } })
            launcher.selectArchiveForMediaRecovery(state.getString("recoveryId"))
            workId = awaitNewMediaWork(manager, workName, previousWork, pickerLaunches)
            assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(workId).get()?.state)
            instrumentation.sendStatus(0, Bundle().apply { putString("archiveCancellation", "RESTORE_CANCELLATION_READY") })
            withTimeout(30_000) { reachedSecondOriginal.await() }
            assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoById(workId).get()?.state)
            val progress = recoveryRecord(state).getJSONObject("summary")
            assertEquals(1, progress.getInt("mediaRecoveryChecked"))
            assertEquals(2, progress.getInt("mediaRecoveryTotal"))
            assertEquals(1, progress.getInt("unavailableOriginals"))
            assertTrue(progress.getBoolean("recoveringMedia"), "A blocked original must never be reported complete")
            val photo = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
            assertContentEquals(asset("proof.png"), delegate.readMedia(photo.mediaRef).data)
            state.put("acceptedPhotoReference", photo.mediaRef)
            state.put("acceptedPhotoHash", checksum(delegate.readMedia(photo.mediaRef).data))
            state.put("cancelledWorkId", workId.toString())
            state.put("retainedSourceHash", checksum(recoverySource(state).readBytes()))
            state.put(
                "noteIds",
                notes.allNotesObserved
                    .first()
                    .map { it.uid.toString() }
                    .sorted()
                    .joinToString(","),
            )
            state.put(
                "draftIds",
                journals
                    .getAllDrafts()
                    .map { it.id.toString() }
                    .sorted()
                    .joinToString(","),
            )
            fixture.writeText(state.toString())
            launcher.cancelRestore()
            withTimeout(15_000) { cancellationObserved.await() }
            awaitWorkFinished(workId)
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(workId).get()?.state)
            assertTrue(recoveryRecord(state).getBoolean("pausedByUser"))
            assertTrue(context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.paused").isFile)
            verifyCancelledAfterRestart()
            cancellationCompleted = true
        } finally {
            if (!cancellationCompleted) {
                launcher.cancelRestore()
                workId?.let { manager.cancelWorkById(it).result.get() }
            }
            bindings.restore()
        }
    }

    private suspend fun verifyCancelledAfterRestart() {
        val state = JSONObject(fixture.readText())
        val record = recoveryRecord(state)
        assertTrue(record.getBoolean("pausedByUser"), "Restart must retain the user's pause")
        assertTrue(record.getBoolean("metadataApplied"))
        assertEquals(1, record.getJSONArray("pending").length())
        val summary = record.getJSONObject("summary")
        assertEquals(1, summary.getInt("unavailableOriginals"))
        assertEquals(1, summary.getInt("mediaRecoveryChecked"))
        assertEquals(2, summary.getInt("mediaRecoveryTotal"))
        assertEquals(state.getString("retainedSourceHash"), checksum(recoverySource(state).readBytes()))
        val work = WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(state.getString("cancelledWorkId"))).get()
        assertEquals(WorkInfo.State.CANCELLED, assertNotNull(work).state)
        verifyAcceptedCheckpoint(state)
    }

    private suspend fun resumeCancelledRecovery() {
        verifyCancelledAfterRestart()
        val state = JSONObject(fixture.readText())
        val launcher = assertIs<AndroidRestoreLauncher>(koin.get<RestoreLauncher>())
        val manager = WorkManager.getInstance(context)
        val workName = "archive_media_recovery_${state.getString("recoveryId")}"
        val previousWork =
            manager
                .getWorkInfosForUniqueWork(workName)
                .get()
                .map { it.id }
                .toSet()
        val pickerLaunches = trapDocumentPicker(launcher)
        launcher.selectArchiveForMediaRecovery(state.getString("recoveryId"))
        val id = awaitNewMediaWork(manager, workName, previousWork, pickerLaunches)
        assertTrue(id.toString() != state.getString("cancelledWorkId"))
        state.put("resumedWorkId", id.toString())
        fixture.writeText(state.toString())
        awaitWorkFinished(id)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            WorkManager
                .getInstance(context)
                .getWorkInfoById(id)
                .get()
                ?.state,
        )
        verifyCancelledCompletion()
    }

    private fun trapDocumentPicker(launcher: AndroidRestoreLauncher): AtomicInteger {
        val launches = AtomicInteger()
        launcher.setupActivityResultLauncher(
            object : ActivityResultLauncher<Intent>() {
                override fun launch(
                    input: Intent,
                    options: ActivityOptionsCompat?,
                ) {
                    launches.incrementAndGet()
                }

                override fun unregister() = Unit

                override val contract = ActivityResultContracts.StartActivityForResult()
            },
        )
        return launches
    }

    private suspend fun awaitNewMediaWork(
        manager: WorkManager,
        name: String,
        previous: Set<UUID>,
        pickerLaunches: AtomicInteger,
    ): UUID =
        withTimeout(15_000) {
            var resumed: UUID? = null
            while (resumed == null) {
                assertEquals(0, pickerLaunches.get(), "An intact retained archive must resume without selecting another document")
                resumed =
                    manager
                        .getWorkInfosForUniqueWork(name)
                        .get()
                        .firstOrNull { it.id !in previous }
                        ?.id
                if (resumed == null) delay(100)
            }
            assertEquals(0, pickerLaunches.get())
            resumed
        }

    private suspend fun verifyCancelledCompletion() {
        val state = JSONObject(fixture.readText())
        verifyAcceptedCheckpoint(state)
        verifyOriginals(state)
        val record = recoveryRecord(state)
        assertFalse(record.optBoolean("pausedByUser"))
        assertEquals(0, record.optJSONArray("pending")?.length() ?: 0)
        val summary = record.getJSONObject("summary")
        assertEquals(0, summary.optInt("unavailableOriginals"))
        assertEquals(0, summary.optInt("unavailablePreviews"))
        assertFalse(summary.optBoolean("recoveringMedia"))
        assertEquals(1, summary.getInt("notesImported"))
        assertEquals(1, summary.getInt("draftsImported"))
        assertEquals(3, summary.getInt("mediaImported"))
        val manager = WorkManager.getInstance(context)
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(UUID.fromString(state.getString("resumedWorkId"))).get()?.state)
        val cancelledState = manager.getWorkInfoById(UUID.fromString(state.getString("cancelledWorkId"))).get()?.state
        assertTrue(cancelledState == null || cancelledState == WorkInfo.State.CANCELLED, "Replaced work must stay cancelled or be pruned")
    }

    private suspend fun verifyAcceptedCheckpoint(state: JSONObject) {
        verifyEdits(state)
        val photo = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
        assertEquals(state.getString("acceptedPhotoReference"), photo.mediaRef)
        assertEquals(state.getString("acceptedPhotoHash"), checksum(media.readMedia(photo.mediaRef).data))
        assertEquals(
            state.getString("noteIds"),
            notes.allNotesObserved
                .first()
                .map { it.uid.toString() }
                .sorted()
                .joinToString(","),
        )
        assertEquals(
            state.getString("draftIds"),
            journals
                .getAllDrafts()
                .map { it.id.toString() }
                .sorted()
                .joinToString(","),
        )
    }

    private suspend fun awaitWorkFinished(id: UUID) =
        withTimeout(60_000) {
            val manager = WorkManager.getInstance(context)
            while (manager
                    .getWorkInfoById(id)
                    .get()
                    ?.state
                    ?.isFinished != true
            ) {
                delay(100)
            }
        }

    private fun recoveryRecord(state: JSONObject) =
        JSONObject(
            context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText(),
        )

    private fun recoverySource(state: JSONObject): File {
        val record = recoveryRecord(state)
        return context.filesDir.resolve("archive_recovery/sources/${record.getString("id")}.${record.getString("sourceGeneration")}.zip")
    }

    private fun checksum(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun cleanupRejectedFixture() {
        val id = Uuid.parse(requireNotNull(InstrumentationRegistry.getArguments().getString("rejectedFixtureId"))).toString()
        val recordFile = context.filesDir.resolve("archive_recovery/records/$id.json")
        if (!recordFile.exists()) return
        val record = JSONObject(recordFile.readText())
        require(!record.optBoolean("metadataApplied") && (record.optJSONArray("pending")?.length() ?: 0) == 0)
        val generation = record.optString("sourceGeneration").takeIf { it.isNotBlank() }
        val source = context.filesDir.resolve("archive_recovery/sources/$id${generation?.let { ".$it" }.orEmpty()}.zip")
        java.util.zip.ZipFile(source).use { zip ->
            val metadata =
                JSONObject(zip.getInputStream(requireNotNull(zip.getEntry("metadata.json"))).bufferedReader().use { it.readText() })
            require(metadata.getString("userId") == "synthetic-recovery" && metadata.optString("version", "2.0").startsWith("2."))
            require(zip.getEntry("manifest.json") == null && zip.getEntry("notes.json") != null)
        }
        androidx.work.WorkManager
            .getInstance(context)
            .cancelUniqueWork("archive_metadata_recovery_$id")
            .result
            .get()
        check(source.delete())
        check(recordFile.delete())
    }

    private suspend fun seed(recoverableSource: Boolean = false) {
        assertFalse(fixture.exists(), "Use a new acceptance fixture, preserving existing emulator content")
        val state =
            JSONObject().apply {
                listOf("note", "draft", "video", "text", "journal", "extraJournal").forEach { put(it, Uuid.random().toString()) }
            }
        val archive = archive(state, complete = recoverableSource)
        val bindings = RestoreWorkerTestBindings()
        val original = media
        val unavailable =
            object : MediaManager by original {
                override suspend fun saveMediaFromFile(
                    sourceFilePath: String,
                    fileName: String,
                    mimeType: String,
                ): String {
                    if (fileName.startsWith("replacement-")) throw IOException("Synthetic original save interrupted")
                    return original.saveMediaFromFile(sourceFilePath, fileName, mimeType)
                }
            }
        val summary =
            try {
                if (recoverableSource) loadKoinModules(module { single<MediaManager> { unavailable } })
                summary(worker(archive).doWork())
            } finally {
                bindings.restore()
            }
        assertEquals(2, summary.unavailableOriginals)
        assertEquals(0, summary.unavailablePreviews)
        assertEquals(1, summary.notesImported)
        assertEquals(1, summary.draftsImported)
        assertEquals(1, summary.mediaImported)
        state.put("recoveryId", requireNotNull(summary.recoveryId))
        state.put("originalSource", archive.absolutePath)
        val noteId = state.id("note")
        val originalNote = assertIs<JournalNote.Image>(notes.getNoteById(noteId))
        assertTrue(originalNote.mediaRef.startsWith("logdate-unavailable://archive/${summary.recoveryId}/"))
        assertTrue(
            notes.compareAndSetNoteForRestore(
                originalNote,
                originalNote.copy(caption = "Edited after partial recovery", lastUpdated = Clock.System.now()),
                notes.getNoteRecoveryScope(),
            ),
        )
        val draft = assertNotNull(journals.getDraftForRestore(state.id("draft")))
        val video = assertIs<SerializableVideoBlock>(draft.blocks[1])
        assertTrue(assertNotNull(video.uri).startsWith("logdate-unavailable://archive/${summary.recoveryId}/"))
        val thumbnail = assertNotNull(video.thumbnailUri)
        assertContentEquals(asset("proof.png"), media.readMedia(thumbnail).data)
        state.put("thumbnail", thumbnail)
        journals.saveDraft(
            draft.copy(
                blocks =
                    listOf(
                        video.copy(caption = "Edited video caption"),
                        SerializableTextBlock(state.id("text"), Clock.System.now(), content = "New text after recovery"),
                    ),
            ),
        )
        journals.create(Journal(state.id("extraJournal"), "Recovery membership added on replacement"))
        links.addContentToJournal(noteId, state.id("extraJournal"))
        fixture.writeText(state.toString())
        assertTrue(
            context.filesDir
                .resolve("archive_recovery/sources")
                .listFiles()
                .orEmpty()
                .any { it.name.startsWith("${summary.recoveryId}.") },
        )
        archive.delete()
    }

    private suspend fun verifyRetainedWaitingState() {
        require(koin.get<app.logdate.client.datastore.SessionStorage>().getSession() == null)
        val state = JSONObject(fixture.readText())
        val worker =
            TestListenableWorkerBuilder<ArchiveMediaRecoveryWorker>(context)
                .setInputData(Data.Builder().putString(RestoreWorker.RECOVERY_ID_KEY, state.getString("recoveryId")).build())
                .build()
        assertIs<ListenableWorker.Result.Success>(worker.doWork())
        val record = JSONObject(context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText())
        val retained = json.decodeFromString<RestoreSummary>(record.getJSONObject("summary").toString())
        assertEquals(2, retained.unavailableOriginals)
        assertFalse(retained.recoveringMedia)
        assertEquals(2, record.getJSONArray("pending").length())
        verifyEdits(state)
    }

    private suspend fun automaticRepair() {
        val state = JSONObject(fixture.readText())
        val complete = archive(state, complete = true)
        try {
            val work =
                androidx.work
                    .OneTimeWorkRequestBuilder<RestoreWorker>()
                    .setInputData(
                        Data
                            .Builder()
                            .putString(RestoreWorker.RECOVERY_ID_KEY, state.getString("recoveryId"))
                            .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(complete).toString())
                            .putBoolean(RestoreWorker.REPLACEMENT_SOURCE_KEY, true)
                            .build(),
                    ).build()
            val manager = androidx.work.WorkManager.getInstance(context)
            manager
                .enqueueUniqueWork(
                    "archive_metadata_recovery_${state.getString("recoveryId")}",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    work,
                ).result
                .get()
            kotlinx.coroutines.withTimeout(30_000) {
                while (manager
                        .getWorkInfoById(work.id)
                        .get()
                        ?.state
                        ?.isFinished != true
                ) {
                    kotlinx.coroutines.delay(100)
                }
            }
            assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(work.id).get()?.state)
            verifyEdits(state)
            verifyOriginals(state)
        } finally {
            complete.delete()
        }
    }

    private suspend fun repair() {
        val state = JSONObject(fixture.readText())
        verifyEdits(state)
        // The original picker file no longer exists; the same request must use its private source.
        val resumed = worker(File(state.getString("originalSource")), state.getString("recoveryId"))
        val resumedSummary = summary(resumed.doWork())
        assertEquals(2, resumedSummary.unavailableOriginals)
        verifyEdits(state)
        val replacement = archive(state, complete = true)
        try {
            val repaired = summary(worker(replacement, state.getString("recoveryId"), replacement = true).doWork())
            assertEquals(0, repaired.unavailableOriginals)
            assertEquals(0, repaired.unavailablePreviews)
            assertEquals(3, repaired.mediaImported)
            assertEquals(2, repaired.mediaRecoveryChecked)
            assertEquals(2, repaired.mediaRecoveryTotal)
            verifyEdits(state)
            verifyOriginals(state)
            assertFalse(
                context.filesDir.resolve("archive_recovery/sources").listFiles().orEmpty().any {
                    it.name.startsWith("${repaired.recoveryId}.")
                },
            )
        } finally {
            replacement.delete()
        }
    }

    private suspend fun verify() {
        val state = JSONObject(fixture.readText())
        verifyEdits(state)
        verifyOriginals(state)
        val photoBeforeReplay = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note"))).mediaRef
        val videoBeforeReplay =
            assertNotNull(journals.getDraftForRestore(state.id("draft")))
                .blocks
                .filterIsInstance<SerializableVideoBlock>()
                .single()
                .uri
        val mediaObjectsBeforeReplay = mediaObjectPaths()
        val recordedMediaImported =
            JSONObject(context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText())
                .getJSONObject("summary")
                .getInt("mediaImported")
        val completed = summary(worker(File(state.getString("originalSource")), state.getString("recoveryId")).doWork())
        assertEquals(0, completed.unavailableOriginals)
        assertEquals(recordedMediaImported, completed.mediaImported, "replay must not change the cumulative imported-media count")
        assertEquals(photoBeforeReplay, assertIs<JournalNote.Image>(notes.getNoteById(state.id("note"))).mediaRef)
        assertEquals(
            videoBeforeReplay,
            assertNotNull(journals.getDraftForRestore(state.id("draft")))
                .blocks
                .filterIsInstance<SerializableVideoBlock>()
                .single()
                .uri,
        )
        assertEquals(mediaObjectsBeforeReplay, mediaObjectPaths(), "replay must not create additional stored media objects")
        val record = JSONObject(context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText())
        assertTrue(record.getBoolean("metadataApplied"))
        assertEquals(0, record.optJSONArray("pending")?.length() ?: 0)
    }

    private fun mediaObjectPaths(): Set<String> =
        context.filesDir
            .resolve("media/objects/sha256")
            .walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(context.filesDir).invariantSeparatorsPath }
            .toSet()

    private suspend fun verifyEdits(state: JSONObject) {
        val note = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
        assertEquals("Edited after partial recovery", note.caption)
        val draft = assertNotNull(journals.getDraftForRestore(state.id("draft")))
        assertEquals(listOf(state.id("video"), state.id("text")), draft.blocks.map { it.id })
        val video = assertIs<SerializableVideoBlock>(draft.blocks.first())
        assertEquals("Edited video caption", video.caption)
        assertEquals(state.getString("thumbnail"), video.thumbnailUri)
        assertEquals("New text after recovery", assertIs<SerializableTextBlock>(draft.blocks[1]).content)
        val memberships =
            links
                .observeJournalsForContent(state.id("note"))
                .first()
                .map { it.id }
                .toSet()
        assertEquals(setOf(state.id("journal"), state.id("extraJournal")), memberships)
    }

    private suspend fun verifyOriginals(state: JSONObject) {
        val photo = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
        val bytes = media.readMedia(photo.mediaRef).data
        assertContentEquals(asset("proof.png"), bytes)
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val draft = assertNotNull(journals.getDraftForRestore(state.id("draft")))
        val video = assertIs<SerializableVideoBlock>(draft.blocks.first())
        val videoBytes = media.readMedia(assertNotNull(video.uri)).data
        assertContentEquals(asset("proof.mp4"), videoBytes)
        val file = File.createTempFile("archive-recovered-video-", ".mp4", context.cacheDir)
        val player = MediaPlayer()
        try {
            file.writeBytes(videoBytes)
            val finished = CountDownLatch(1)
            player.setOnCompletionListener { finished.countDown() }
            player.setDataSource(file.absolutePath)
            player.prepare()
            assertTrue(player.duration > 0)
            player.start()
            assertTrue(finished.await(20, TimeUnit.SECONDS))
        } finally {
            player.release()
            file.delete()
        }
    }

    private fun worker(
        archive: File,
        recoveryId: String? = null,
        replacement: Boolean = false,
    ): RestoreWorker =
        TestListenableWorkerBuilder<RestoreWorker>(context)
            .setInputData(
                Data
                    .Builder()
                    .putString(RestoreWorker.SOURCE_URI_KEY, Uri.fromFile(archive).toString())
                    .putString(RestoreWorker.RECOVERY_ID_KEY, recoveryId)
                    .putBoolean(RestoreWorker.REPLACEMENT_SOURCE_KEY, replacement)
                    .putBoolean(RestoreWorker.DELETE_SOURCE_AFTER_RESTORE_KEY, recoveryId != null && !replacement)
                    .build(),
            ).build()

    private fun summary(result: ListenableWorker.Result): RestoreSummary =
        json.decodeFromString(
            assertNotNull(
                assertIs<ListenableWorker.Result.Success>(
                    result,
                    "Worker result: $result",
                ).outputData.getString(RestoreWorker.SUMMARY_JSON_KEY),
            ),
        )

    private fun archive(
        state: JSONObject,
        complete: Boolean,
    ): File {
        val now = Clock.System.now() - 2.days
        val note =
            ExportNote(
                state.getString("note"),
                "image",
                mediaPath = "old/photo.png",
                caption = "Archive caption",
                createdAt = now,
                updatedAt = now,
            )
        val video =
            SerializableVideoBlock(
                state.id("video"),
                now,
                uri = "old/video.mp4",
                thumbnailUri = "old/preview.png",
                caption = "Archive video",
            )
        val text = SerializableTextBlock(state.id("text"), now, content = "Archive text")
        val draft = ExportDraft(state.getString("draft"), content = "", createdAt = now, updatedAt = now, blocks = listOf(text, video))
        val manifest =
            ExportMediaManifest(
                listOf(
                    ExportMediaFile(
                        sourceUri = "old/photo.png",
                        exportPath = if (complete) "media/replacement-photo.png" else "media/absent-photo.png",
                    ),
                    ExportMediaFile(
                        sourceUri = "old/video.mp4",
                        exportPath = if (complete) "media/replacement-video.mp4" else "media/absent-video.mp4",
                    ),
                    ExportMediaFile(sourceUri = "old/preview.png", exportPath = "media/preview.png"),
                ),
            )
        val metadata =
            ExportMetadata(
                version = ExportSchemaVersion.V1_2,
                exportDate = now,
                userId = "synthetic-recovery",
                deviceId = "synthetic-source",
                appVersion = "1.0",
                stats = ExportStats(1, 1, 1, 3),
            )
        val files =
            mapOf(
                "metadata.json" to json.encodeToString(metadata),
                "journals.json" to json.encodeToString(mapOf("journals" to listOf(Journal(state.id("journal"), "Archive media recovery")))),
                "notes.json" to json.encodeToString(mapOf("notes" to listOf(note))),
                "drafts.json" to json.encodeToString(mapOf("drafts" to listOf(draft))),
                "journal_notes.json" to
                    json.encodeToString(
                        mapOf(
                            "journal_notes" to listOf(ExportJournalNoteRelation(state.getString("journal"), state.getString("note"), now)),
                        ),
                    ),
                "media_manifest.json" to json.encodeToString(manifest),
            )
        return File.createTempFile("archive-media-recovery-", ".zip", context.cacheDir).also { target ->
            ZipOutputStream(target.outputStream()).use { zip ->
                files.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.encodeToByteArray())
                    zip.closeEntry()
                }
                val attachments = mutableMapOf("media/preview.png" to asset("proof.png"))
                if (complete) {
                    attachments["media/replacement-photo.png"] = asset("proof.png")
                    attachments["media/replacement-video.mp4"] =
                        asset("proof.mp4")
                }
                attachments.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun asset(name: String): ByteArray =
        instrumentation.context.assets
            .open("recovery/$name")
            .use { it.readBytes() }

    private fun JSONObject.id(key: String): Uuid = Uuid.parse(getString(key))
}
