package app.logdate.client.data.restore

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NativePendingCameraDraftImporterTest {
    @Test
    fun `v4 draft stores without camera work do not require a media inventory`() {
        val now = Clock.System.now()
        val draft = EntryDraft(id = Uuid.random(), notes = emptyList(), createdAt = now, updatedAt = now)

        assertFalse(hasPendingCameraOriginal(draft))
    }

    @Test
    fun `camera capture destination rejects a symlinked directory`() {
        val root = Files.createTempDirectory("camera-custody-").toFile()
        val external = File(root, "external").apply { check(mkdir()) }
        val filesDir = File(root, "files").apply { check(mkdir()) }
        try {
            Files.createSymbolicLink(File(filesDir, "camera-captures").toPath(), external.toPath())
            assertFailsWith<IllegalStateException> { ensurePrivateCameraCaptureDirectory(filesDir) }
            assertTrue(external.listFiles().isNullOrEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `camera draft is not admitted while another pending original points outside verified custody`() {
        val now = Clock.System.now()
        val cameraPath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/photo.jpg.tmp"
        val draft =
            EntryDraft(
                id = Uuid.random(),
                notes = emptyList(),
                createdAt = now,
                updatedAt = now,
                pendingMedia =
                    listOf(
                        PendingMediaRecord(Uuid.random(), PendingMediaType.CAMERA_PHOTO, now, filePath = cameraPath),
                        PendingMediaRecord(
                            Uuid.random(),
                            PendingMediaType.AUDIO,
                            now,
                            filePath = "/old-device/audio_notes/staging/recording.m4a",
                        ),
                    ),
            )

        runTest { assertFalse(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { false }) }
    }

    @Test
    fun `camera draft is admitted when its only referenced original is recovered`() {
        val now = Clock.System.now()
        val cameraPath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/photo.jpg.tmp"
        val draft =
            EntryDraft(
                id = Uuid.random(),
                notes = emptyList(),
                createdAt = now,
                updatedAt = now,
                pendingMedia =
                    listOf(
                        PendingMediaRecord(Uuid.random(), PendingMediaType.CAMERA_PHOTO, now, filePath = cameraPath),
                    ),
            )

        runTest { assertTrue(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { false }) }
    }

    @Test
    fun `camera draft is not admitted while a second reference has not been recovered`() {
        val now = Clock.System.now()
        val cameraPath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/photo.jpg.tmp"
        val canonical = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"
        val draft =
            EntryDraft(
                id = Uuid.random(),
                notes = emptyList(),
                createdAt = now,
                updatedAt = now,
                pendingMedia =
                    listOf(
                        PendingMediaRecord(
                            Uuid.random(),
                            PendingMediaType.CAMERA_PHOTO,
                            now,
                            filePath = cameraPath,
                            fallbackFilePath = canonical,
                        ),
                    ),
            )

        runTest { assertFalse(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { false }) }
    }

    @Test
    fun `canonical fallback survives only after destination integrity verification`() =
        runTest {
            val now = Clock.System.now()
            val cameraPath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/photo.jpg.tmp"
            val canonical = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia =
                        listOf(
                            PendingMediaRecord(
                                Uuid.random(),
                                PendingMediaType.CAMERA_PHOTO,
                                now,
                                filePath = cameraPath,
                                fallbackFilePath = canonical,
                            ),
                        ),
                )

            assertTrue(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { it == canonical })
            assertFalse(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { false })
        }

    @Test
    fun `note media reference survives only when already valid at destination`() =
        runTest {
            val now = Clock.System.now()
            val cameraPath = "/data/user/0/studio.hypertext.logdate/files/camera-captures/photo.jpg.tmp"
            val canonical = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.jpg"
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = listOf(JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = canonical)),
                    createdAt = now,
                    updatedAt = now,
                    pendingMedia = listOf(PendingMediaRecord(Uuid.random(), PendingMediaType.CAMERA_PHOTO, now, filePath = cameraPath)),
                )

            assertTrue(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { it == canonical })
            assertFalse(entryDraftCameraMediaIsComplete(draft, setOf(cameraPath)) { false })
        }
}
