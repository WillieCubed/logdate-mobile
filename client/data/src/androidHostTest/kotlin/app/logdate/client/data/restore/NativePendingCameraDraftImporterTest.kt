package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.data.notes.drafts.AndroidLocalEntryDraftStore
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NativePendingCameraDraftImporterTest {
    @Test
    fun `retained extensionless payload is read through private preferences suffix copy`() =
        runTest {
            val root = Files.createTempDirectory("retained-draft-read-").toFile()
            val sourceStoreJob = SupervisorJob()
            val sourceStoreScope = CoroutineScope(sourceStoreJob + Dispatchers.IO)
            try {
                val draft =
                    EntryDraft(
                        id = Uuid.random(),
                        notes = emptyList(),
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    )
                val sourcePreferences = File(root, "entry_drafts.preferences_pb")
                val sourceStore = PreferenceDataStoreFactory.create(scope = sourceStoreScope, produceFile = { sourcePreferences })
                AndroidLocalEntryDraftStore(sourceStore).saveDraft(draft)
                sourceStoreJob.cancelAndJoin()

                val payload = File(File(root, "records/fixture"), "payload")
                check(payload.parentFile!!.mkdirs())
                Files.copy(sourcePreferences.toPath(), payload.toPath())
                val scratchRoot = File(root, "no_backup/retained-draft-reads")

                assertEquals(listOf(draft), readRetainedEntryDrafts(payload, scratchRoot))
                assertTrue(scratchRoot.listFiles().isNullOrEmpty())
            } finally {
                sourceStoreJob.cancelAndJoin()
                root.deleteRecursively()
            }
        }

    @Test
    fun `incomplete table coverage imports matching receipt draft once and keeps source custody`() =
        runTest {
            var importedDraftIds = emptySet<String>()
            var sourceInCustody = true
            var admissions = 0
            val stableDraftId = "transfer-draft-20"

            suspend fun reconcile(coverageComplete: Boolean) {
                assertTrue(
                    importReceiptPinnedCameraDrafts(
                        coverageComplete = coverageComplete,
                        import = {
                            importedDraftIds = importedDraftIds + stableDraftId
                            true
                        },
                        admitSource = {
                            admissions += 1
                            sourceInCustody = false
                        },
                    ),
                )
            }

            reconcile(coverageComplete = false)
            assertEquals(setOf(stableDraftId), importedDraftIds)
            assertEquals(0, admissions)
            assertTrue(sourceInCustody)

            reconcile(coverageComplete = false)
            assertEquals(setOf(stableDraftId), importedDraftIds)
            assertEquals(0, admissions)
            assertTrue(sourceInCustody)

            reconcile(coverageComplete = true)
            assertEquals(setOf(stableDraftId), importedDraftIds)
            assertEquals(1, admissions)
            assertFalse(sourceInCustody)
        }

    @Test
    fun `failed receipt or owner fence leaves source custody untouched`() =
        runTest {
            var sourceInCustody = true
            var admittedDraft = false
            val result =
                importReceiptPinnedCameraDrafts(
                    coverageComplete = true,
                    import = { false },
                    admitSource = {
                        admittedDraft = true
                        sourceInCustody = false
                    },
                )

            assertFalse(result)
            assertFalse(admittedDraft)
            assertTrue(sourceInCustody)
        }

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
    fun `camera publication preserves an existing target and rejects different bytes`() {
        val root = Files.createTempDirectory("camera-publish-").toFile()
        try {
            val target = File(root, "target.jpg").apply { writeBytes("existing".encodeToByteArray()) }
            val same = File(root, "same.part").apply { writeBytes("existing".encodeToByteArray()) }
            publishVerifiedCameraCapture(same, target) { it.readBytes().contentEquals("existing".encodeToByteArray()) }
            assertEquals("existing", target.readText())

            val different = File(root, "different.part").apply { writeBytes("replacement".encodeToByteArray()) }
            assertFailsWith<IllegalStateException> {
                publishVerifiedCameraCapture(different, target) { it.readBytes().contentEquals("replacement".encodeToByteArray()) }
            }
            assertEquals("existing", target.readText())
            assertTrue(different.exists())
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
