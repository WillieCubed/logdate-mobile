package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DesktopLocalEntryDraftStoreTest {
    private lateinit var temporaryAppDataDirectory: File
    private lateinit var store: DesktopLocalEntryDraftStore

    @BeforeTest
    fun setUp() {
        temporaryAppDataDirectory = createTempDirectory("logdate-drafts-").toFile()
        store = DesktopLocalEntryDraftStore(appDataDirectory = temporaryAppDataDirectory)
    }

    @AfterTest
    fun tearDown() {
        temporaryAppDataDirectory.deleteRecursively()
    }

    @Test
    fun completeDraftSnapshotRoundTrips() =
        runTest {
            val timestamp = Instant.fromEpochMilliseconds(1_725_000_000_000)
            val pendingMedia =
                PendingMediaRecord(
                    blockId = Uuid.random(),
                    mediaType = PendingMediaType.AUDIO,
                    createdAt = timestamp,
                    filePath = "/recordings/pending.m4a",
                )
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes =
                        listOf(
                            JournalNote.Image(
                                uid = Uuid.random(),
                                mediaRef = "image.jpg",
                                caption = "Durable caption",
                                creationTimestamp = timestamp,
                                lastUpdated = timestamp,
                            ),
                        ),
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    pendingMedia = listOf(pendingMedia),
                    selectedJournalIds = listOf(Uuid.random()),
                )

            store.saveDraft(draft)

            assertEquals(draft, store.getDraft(draft.id))
            assertEquals(listOf(draft), store.getAllDrafts())
        }

    @Test
    fun legacyDraftWithoutSnapshotFieldsUsesEmptyDefaults() =
        runTest {
            val draftId = Uuid.random()
            val draftsDirectory = File(temporaryAppDataDirectory, "drafts")
            File(draftsDirectory, "index.json").writeText("[\"$draftId\"]")
            File(draftsDirectory, "$draftId.json").writeText(
                """
                {
                  "id": "$draftId",
                  "notes": [],
                  "createdAt": 1725000000000,
                  "updatedAt": 1725000000000
                }
                """.trimIndent(),
            )

            val restored = assertNotNull(store.getDraft(draftId))

            assertTrue(restored.pendingMedia.isEmpty())
            assertTrue(restored.selectedJournalIds.isEmpty())
        }

    @Test
    fun malformedDraftSnapshotSurfacesRecoverableErrorAndPreservesFile() =
        runTest {
            val draftId = Uuid.random()
            val malformed = "{ definitely-not-a-draft"
            val draftFile = File(temporaryAppDataDirectory, "drafts/$draftId.json")
            draftFile.writeText(malformed)

            assertFailsWith<EntryDraftStorageException> {
                store.getDraft(draftId)
            }
            assertEquals(malformed, draftFile.readText())
        }

    @Test
    fun malformedIndexedSnapshotFailsWholeLoadAndPreservesFiles() =
        runTest {
            val draftId = Uuid.random()
            val draftsDirectory = File(temporaryAppDataDirectory, "drafts")
            val index = File(draftsDirectory, "index.json")
            val draftFile = File(draftsDirectory, "$draftId.json")
            index.writeText("[\"$draftId\"]")
            draftFile.writeText("{ malformed")

            assertFailsWith<EntryDraftStorageException> {
                store.getAllDrafts()
            }
            assertEquals("[\"$draftId\"]", index.readText())
            assertEquals("{ malformed", draftFile.readText())
        }

    @Test
    fun malformedDraftIndexSurfacesRecoverableErrorAndPreservesIndex() =
        runTest {
            val index = File(temporaryAppDataDirectory, "drafts/index.json")
            index.writeText("not-json")

            assertFailsWith<EntryDraftStorageException> {
                store.getAllDrafts()
            }
            assertEquals("not-json", index.readText())
        }
}
