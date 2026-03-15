package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportLocation
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RestoreUserDataUseCaseTest {
    private lateinit var journalRepo: FakeJournalRepository
    private lateinit var notesRepo: FakeJournalNotesRepository
    private lateinit var contentRepo: FakeJournalContentRepository
    private lateinit var useCase: RestoreUserDataUseCase

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Clock.System.now()

    @BeforeTest
    fun setUp() {
        journalRepo = FakeJournalRepository()
        notesRepo = FakeJournalNotesRepository()
        contentRepo = FakeJournalContentRepository()
        useCase = RestoreUserDataUseCase(journalRepo, notesRepo, contentRepo)
    }

    // region Happy path

    @Test
    fun `restore imports journals notes and relations from valid JSON`() =
        runTest {
            val journalId = Uuid.random()
            val noteId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId)),
                    notes = listOf(testTextNote(noteId)),
                    relations = listOf(ExportJournalNoteRelation(journalId.toString(), noteId.toString(), now)),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.journalsImported)
            assertEquals(1, result.notesImported)
            assertEquals(1, result.journalLinksImported)
            assertTrue(result.warnings.isEmpty())
            assertEquals(1, journalRepo.created.size)
            assertEquals(1, notesRepo.created.size)
            assertEquals(1, contentRepo.links.size)
        }

    @Test
    fun `restore preserves text note content`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, content = "Hello from the backup")),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Text
            assertEquals("Hello from the backup", restored.content)
            assertEquals(noteId, restored.uid)
        }

    @Test
    fun `restore handles image video and audio notes`() =
        runTest {
            val imageId = Uuid.random()
            val videoId = Uuid.random()
            val audioId = Uuid.random()

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = imageId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            ExportNote(
                                id = videoId.toString(),
                                type = "video",
                                mediaPath = "file:///clip.mp4",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            ExportNote(
                                id = audioId.toString(),
                                type = "audio",
                                mediaPath = "file:///memo.m4a",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(3, result.notesImported)
            assertTrue(result.warnings.isEmpty())

            val types = notesRepo.created.map { it::class }
            assertTrue(types.contains(JournalNote.Image::class))
            assertTrue(types.contains(JournalNote.Video::class))
            assertTrue(types.contains(JournalNote.Audio::class))
        }

    @Test
    fun `restore creates journal-note relations`() =
        runTest {
            val j1 = Uuid.random()
            val j2 = Uuid.random()
            val noteId = Uuid.random()

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(j1), testJournal(j2, title = "Second")),
                    notes = listOf(testTextNote(noteId)),
                    relations =
                        listOf(
                            ExportJournalNoteRelation(j1.toString(), noteId.toString(), now),
                            ExportJournalNoteRelation(j2.toString(), noteId.toString(), now),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(2, result.journalLinksImported)
            assertEquals(2, contentRepo.links.size)
            assertTrue(contentRepo.links.any { it.first == noteId && it.second == j1 })
            assertTrue(contentRepo.links.any { it.first == noteId && it.second == j2 })
        }

    @Test
    fun `restore imports drafts with text and media blocks`() =
        runTest {
            val draftId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts =
                        listOf(
                            testDraft(
                                draftId,
                                content = "Draft text",
                                mediaReferences = listOf("file:///photo.jpg"),
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertTrue(saved.blocks.any { it is SerializableTextBlock })
            assertTrue(saved.blocks.any { it is SerializableCameraBlock })
        }

    // endregion

    // region Merge strategies

    @Test
    fun `MERGE_KEEP_NEWEST skips journal when existing is newer`() =
        runTest {
            val journalId = Uuid.random()
            val newerTimestamp = now
            val olderTimestamp = now - 7.days

            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = newerTimestamp)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, lastUpdated = olderTimestamp)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.MERGE_KEEP_NEWEST))

            assertEquals(0, result.journalsImported)
            assertTrue(journalRepo.created.isEmpty())
            assertTrue(journalRepo.updated.isEmpty())
        }

    @Test
    fun `MERGE_KEEP_NEWEST overwrites journal when incoming is newer`() =
        runTest {
            val journalId = Uuid.random()
            val olderTimestamp = now - 7.days
            val newerTimestamp = now

            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = olderTimestamp)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, title = "Updated", lastUpdated = newerTimestamp)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.MERGE_KEEP_NEWEST))

            assertEquals(1, result.journalsImported)
            assertEquals("Updated", journalRepo.updated.first().title)
        }

    @Test
    fun `REPLACE_EXISTING always overwrites existing journal`() =
        runTest {
            val journalId = Uuid.random()
            journalRepo.existingJournals[journalId] = testJournal(journalId, lastUpdated = now)

            val bundle =
                buildBundle(
                    journals = listOf(testJournal(journalId, title = "Replaced", lastUpdated = now - 30.days)),
                )

            val result = useCase.restore(bundle, RestoreOptions(strategy = RestoreStrategy.REPLACE_EXISTING))

            assertEquals(1, result.journalsImported)
            assertEquals("Replaced", journalRepo.updated.first().title)
        }

    @Test
    fun `MERGE_KEEP_NEWEST skips note when existing is newer`() =
        runTest {
            val noteId = Uuid.random()
            notesRepo.existingNotes[noteId] =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = "Local version",
                )

            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, updatedAt = now - 7.days)),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.notesImported)
        }

    @Test
    fun `MERGE_KEEP_NEWEST overwrites note when incoming is newer`() =
        runTest {
            val noteId = Uuid.random()
            notesRepo.existingNotes[noteId] =
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = now - 14.days,
                    lastUpdated = now - 7.days,
                    content = "Old version",
                )

            val bundle =
                buildBundle(
                    notes = listOf(testTextNote(noteId, content = "New version", updatedAt = now)),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
        }

    // endregion

    // region Error handling

    @Test
    fun `invalid UUID in notes adds warning and skips`() =
        runTest {
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = "not-a-uuid",
                                type = "text",
                                content = "Will be skipped",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.notesImported)
            assertEquals(1, result.warnings.size)
            assertTrue(result.warnings.first().contains("Invalid UUID"))
        }

    @Test
    fun `invalid UUID in relations adds warning and skips`() =
        runTest {
            val bundle =
                buildBundle(
                    relations =
                        listOf(
                            ExportJournalNoteRelation("bad-uuid", Uuid.random().toString(), now),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.journalLinksImported)
            assertTrue(result.warnings.any { it.contains("Invalid UUID") })
        }

    @Test
    fun `unsupported note type adds warning and continues`() =
        runTest {
            val validId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "sketch",
                                content = "Future type",
                                createdAt = now,
                                updatedAt = now,
                            ),
                            testTextNote(validId, content = "Valid note"),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
            assertTrue(result.warnings.any { it.contains("Unsupported note type") })
        }

    @Test
    fun `media note without media path adds warning`() =
        runTest {
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(0, result.notesImported)
            assertTrue(result.warnings.any { it.contains("Missing media reference") })
        }

    // endregion

    // region Media import

    @Test
    fun `MediaImporter is invoked for media notes`() =
        runTest {
            val mediaImporter = FakeMediaImporter(mapOf("file:///photo.jpg" to "content://imported/1"))

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            val result = useCase.restore(bundle, mediaImporter = mediaImporter)

            assertEquals(1, result.notesImported)
            assertEquals(1, result.mediaImported)
            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("content://imported/1", restored.mediaRef)
        }

    @Test
    fun `media manifest maps source URIs to export paths`() =
        runTest {
            val sourceUri = "content://media/external/images/123"
            val exportPath = "media/2026/photo_abc123.jpg"
            val importedUri = "content://imported/resolved"

            val mediaImporter = FakeMediaImporter(mapOf(exportPath to importedUri))

            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = Uuid.random().toString(),
                                type = "image",
                                mediaPath = sourceUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                    mediaManifest =
                        ExportMediaManifest(
                            files =
                                listOf(
                                    ExportMediaFile(
                                        sourceUri = sourceUri,
                                        exportPath = exportPath,
                                    ),
                                ),
                        ),
                )

            val result = useCase.restore(bundle, mediaImporter = mediaImporter)

            assertEquals(1, result.mediaImported)
            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals(importedUri, restored.mediaRef)
        }

    // endregion

    // region Caption preservation

    @Test
    fun `restore preserves caption on image note`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                caption = "Sunset at the beach",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("Sunset at the beach", restored.caption)
        }

    @Test
    fun `restore preserves caption on video note`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "video",
                                mediaPath = "file:///clip.mp4",
                                caption = "Birthday party",
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Video
            assertEquals("Birthday party", restored.caption)
        }

    @Test
    fun `restore uses empty caption when export has no caption`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "image",
                                mediaPath = "file:///photo.jpg",
                                caption = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        ),
                )

            useCase.restore(bundle)

            val restored = notesRepo.created.first() as JournalNote.Image
            assertEquals("", restored.caption)
        }

    // endregion

    // region Edge cases

    @Test
    fun `empty payloads restore successfully with zero counts`() =
        runTest {
            val bundle = buildBundle()

            val result = useCase.restore(bundle)

            assertEquals(0, result.journalsImported)
            assertEquals(0, result.notesImported)
            assertEquals(0, result.draftsImported)
            assertEquals(0, result.journalLinksImported)
            assertEquals(0, result.mediaImported)
            assertTrue(result.warnings.isEmpty())
        }

    @Test
    fun `includeDrafts false skips draft restoration`() =
        runTest {
            val bundle =
                buildBundle(
                    drafts = listOf(testDraft(Uuid.random(), content = "Should be skipped")),
                )

            val result = useCase.restore(bundle, RestoreOptions(includeDrafts = false))

            assertEquals(0, result.draftsImported)
            assertTrue(journalRepo.savedDrafts.isEmpty())
        }

    @Test
    fun `notes with location data preserve coordinates`() =
        runTest {
            val noteId = Uuid.random()
            val bundle =
                buildBundle(
                    notes =
                        listOf(
                            ExportNote(
                                id = noteId.toString(),
                                type = "text",
                                content = "At the park",
                                createdAt = now,
                                updatedAt = now,
                                location =
                                    ExportLocation(
                                        latitude = 37.7749,
                                        longitude = -122.4194,
                                        placeName = "San Francisco",
                                    ),
                            ),
                        ),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.notesImported)
            val restored = notesRepo.created.first() as JournalNote.Text
            assertEquals(37.7749, restored.location?.coordinates?.latitude)
            assertEquals(-122.4194, restored.location?.coordinates?.longitude)
            assertEquals("San Francisco", restored.location?.displayName)
        }

    @Test
    fun `draft with journalId links to correct journal`() =
        runTest {
            val journalId = Uuid.random()
            val draftId = Uuid.random()
            val bundle =
                buildBundle(
                    drafts = listOf(testDraft(draftId, content = "Linked draft", journalId = journalId.toString())),
                )

            val result = useCase.restore(bundle)

            assertEquals(1, result.draftsImported)
            val saved = journalRepo.savedDrafts.first()
            assertTrue(saved.selectedJournalIds.contains(journalId))
        }

    // endregion

    // region Test data builders

    private fun testJournal(
        id: Uuid = Uuid.random(),
        title: String = "Test Journal",
        lastUpdated: Instant = now,
    ) = Journal(
        id = id,
        title = title,
        description = "Description",
        created = now - 30.days,
        lastUpdated = lastUpdated,
    )

    private fun testTextNote(
        id: Uuid = Uuid.random(),
        content: String = "Test note",
        createdAt: Instant = now,
        updatedAt: Instant = now,
    ) = ExportNote(
        id = id.toString(),
        type = "text",
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun testDraft(
        id: Uuid = Uuid.random(),
        content: String = "Draft content",
        journalId: String? = null,
        mediaReferences: List<String> = emptyList(),
    ) = app.logdate.client.domain.export.ExportDraft(
        id = id.toString(),
        journalId = journalId,
        content = content,
        createdAt = now,
        updatedAt = now,
        mediaReferences = mediaReferences,
    )

    private fun buildBundle(
        journals: List<Journal> = emptyList(),
        notes: List<ExportNote> = emptyList(),
        relations: List<ExportJournalNoteRelation> = emptyList(),
        drafts: List<app.logdate.client.domain.export.ExportDraft> = emptyList(),
        mediaManifest: ExportMediaManifest? = null,
    ): RestoreBundle {
        val metadata =
            app.logdate.client.domain.export.ExportMetadata(
                version = "1.0",
                exportDate = now,
                userId = "test-user",
                deviceId = "test-device",
                appVersion = "1.0.0",
                stats =
                    ExportStats(
                        journalCount = journals.size,
                        noteCount = notes.size,
                        draftCount = drafts.size,
                        mediaCount = 0,
                    ),
            )
        return RestoreBundle(
            metadataJson = json.encodeToString(metadata),
            journalsJson = json.encodeToString(mapOf("journals" to journals)),
            notesJson = json.encodeToString(mapOf("notes" to notes)),
            journalNotesJson = json.encodeToString(mapOf("journal_notes" to relations)),
            draftsJson = json.encodeToString(mapOf("drafts" to drafts)),
            mediaManifestJson = mediaManifest?.let { json.encodeToString(it) },
        )
    }

    // endregion

    // region Fakes

    private class FakeJournalRepository : JournalRepository {
        val existingJournals = mutableMapOf<Uuid, Journal>()
        val created = mutableListOf<Journal>()
        val updated = mutableListOf<Journal>()
        val savedDrafts = mutableListOf<EditorDraft>()

        override val allJournalsObserved: Flow<List<Journal>> = flowOf(emptyList())

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(existingJournals[id] ?: Journal(id = id))

        override suspend fun getJournalById(id: Uuid): Journal? = existingJournals[id]

        override suspend fun create(journal: Journal): Uuid {
            created.add(journal)
            existingJournals[journal.id] = journal
            return journal.id
        }

        override suspend fun update(journal: Journal) {
            updated.add(journal)
            existingJournals[journal.id] = journal
        }

        override suspend fun delete(journalId: Uuid) {
            existingJournals.remove(journalId)
        }

        override suspend fun saveDraft(draft: EditorDraft) {
            savedDrafts.add(draft)
        }

        override suspend fun getLatestDraft(): EditorDraft? = null

        override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = null

        override suspend fun deleteDraft(id: Uuid) {
            savedDrafts.removeAll { it.id == id }
        }
    }

    private class FakeJournalNotesRepository : JournalNotesRepository {
        val existingNotes = mutableMapOf<Uuid, JournalNote>()
        val created = mutableListOf<JournalNote>()
        val removed = mutableListOf<Uuid>()

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = existingNotes[noteId]

        override suspend fun create(note: JournalNote): Uuid {
            created.add(note)
            existingNotes[note.uid] = note
            return note.uid
        }

        override suspend fun remove(note: JournalNote) {
            removed.add(note.uid)
            existingNotes.remove(note.uid)
        }

        override suspend fun removeById(noteId: Uuid) {
            removed.add(noteId)
            existingNotes.remove(noteId)
        }

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {
            created.add(note)
            existingNotes[note.uid] = note
        }

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}
    }

    private class FakeJournalContentRepository : JournalContentRepository {
        val links = mutableListOf<Pair<Uuid, Uuid>>()

        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            links.add(contentId to journalId)
        }

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            links.removeAll { it.first == contentId && it.second == journalId }
        }

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) {
            journalIds.forEach { links.add(contentId to it) }
        }

        override suspend fun removeContentFromAllJournals(contentId: Uuid) {}
    }

    private class FakeMediaImporter(
        private val mappings: Map<String, String> = emptyMap(),
    ) : MediaImporter {
        override suspend fun importMedia(exportPath: String): String? = mappings[exportPath]
    }

    // endregion
}
