package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportMediaFile
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeMediaImporter
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class UnavailableArchiveMediaTest : RestoreArchiveTestFixture() {
    @Test
    fun `missing originals preserve rich blocks and independently recover a retained preview`() =
        runTest {
            val draftId = Uuid.random()
            val journal = Journal(Uuid.random(), "Recovered journal")
            val text = SerializableTextBlock(Uuid.random(), now, content = "Keep these words")
            val audio =
                SerializableAudioBlock(
                    Uuid.random(),
                    now,
                    uri = "content://old-provider/recording",
                    duration = 12_345,
                    transcription = "Keep transcript",
                )
            val video =
                SerializableVideoBlock(
                    Uuid.random(),
                    now,
                    uri = "file:///old/original.mp4",
                    thumbnailUri = "file:///old/preview.jpg",
                    caption = "Keep caption",
                )
            val incoming =
                ExportDraft(
                    draftId.toString(),
                    journalIds = listOf(journal.id.toString()),
                    content = "",
                    createdAt = now,
                    updatedAt = now,
                    blocks = listOf(text, audio, video),
                )
            val bundle =
                buildBundle(
                    journals = listOf(journal),
                    drafts = listOf(incoming),
                    mediaManifest =
                        ExportMediaManifest(
                            listOf(ExportMediaFile(sourceUri = "file:///old/preview.jpg", exportPath = "media/preview.jpg")),
                        ),
                )
            val result = useCase.restore(bundle, mediaImporter = FakeMediaImporter(mapOf("media/preview.jpg" to "file:///new/preview.jpg")))
            val saved = journalRepo.savedDrafts.single()
            assertEquals(listOf(text.id, audio.id, video.id), saved.blocks.map { it.id })
            assertEquals(audio.copy(uri = result.unavailableMedia.first { it.blockId == audio.id.toString() }.reference), saved.blocks[1])
            assertEquals(
                video.copy(
                    uri =
                        result.unavailableMedia
                            .first {
                                it.blockId == video.id.toString()
                            }.reference,
                    thumbnailUri = "file:///new/preview.jpg",
                ),
                saved.blocks[2],
            )
            assertEquals(listOf(journal.id), saved.selectedJournalIds)
            assertEquals(setOf(audio.id.toString(), video.id.toString()), result.unavailableMedia.mapNotNull { it.blockId }.toSet())
            assertTrue(result.unavailableMedia.all { it.role == RecoveryMediaRole.ORIGINAL })
            assertEquals(1, result.mediaImported)
        }

    @Test
    fun `an unavailable audio original retains its note details and journal link`() =
        runTest {
            val noteId = Uuid.random()
            val journal = Journal(Uuid.random(), "Recovered journal")
            val incoming =
                ExportNote(
                    noteId.toString(),
                    "audio",
                    mediaPath = "file:///old/recording.m4a",
                    durationMs = 9_876,
                    caption = "Keep caption",
                    createdAt = now,
                    updatedAt = now,
                )
            val result =
                useCase.restore(
                    buildBundle(
                        journals = listOf(journal),
                        notes = listOf(incoming),
                        relations = listOf(ExportJournalNoteRelation(journal.id.toString(), noteId.toString(), now)),
                        mediaManifest = ExportMediaManifest(emptyList()),
                    ),
                    mediaImporter = FakeMediaImporter(),
                )
            val saved = assertIs<JournalNote.Audio>(notesRepo.created.single())
            assertEquals(9_876L, saved.durationMs)
            assertEquals("Keep caption", saved.caption)
            assertEquals(noteId, saved.uid)
            assertEquals(listOf(noteId to journal.id), contentRepo.links)
            assertEquals(noteId.toString(), result.unavailableMedia.single().entityId)
            assertEquals(UnavailableMediaReason.NOT_IN_ARCHIVE, result.unavailableMedia.single().reason)
        }

    @Test
    fun `opting out of media never invokes the importer and retains content metadata`() =
        runTest {
            val id = Uuid.random()
            val result =
                useCase.restore(
                    buildBundle(
                        notes =
                            listOf(
                                ExportNote(
                                    id.toString(),
                                    "image",
                                    mediaPath = "media/photo.jpg",
                                    caption = "Keep caption",
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            ),
                    ),
                    options = RestoreOptions(includeMedia = false),
                    mediaImporter =
                        object : MediaImporter {
                            override suspend fun importMedia(exportPath: String): String? = error("media import disabled")
                        },
                )
            assertEquals("Keep caption", assertIs<JournalNote.Image>(notesRepo.created.single()).caption)
            assertEquals(UnavailableMediaReason.NOT_SELECTED, result.unavailableMedia.single().reason)
        }

    @Test
    fun `newer archive metadata keeps a working original without deleting the note`() =
        runTest {
            assertExistingOriginalSurvives(includeMedia = true)
        }

    @Test
    fun `media deselection keeps a working original while replacing metadata`() =
        runTest {
            assertExistingOriginalSurvives(includeMedia = false)
        }

    @Test
    fun `a matching draft block keeps its working original and preview when archive originals are omitted`() =
        runTest {
            val id = Uuid.random()
            val block =
                SerializableVideoBlock(
                    Uuid.random(),
                    now,
                    uri = "file:///accepted/video.mp4",
                    thumbnailUri = "file:///accepted/preview.jpg",
                    caption = "Old caption",
                )
            journalRepo.savedDrafts.add(EditorDraft(id, listOf(block), emptyList(), now, now - 1.days))
            val incoming =
                ExportDraft(
                    id.toString(),
                    content = "",
                    createdAt = now,
                    updatedAt = now,
                    blocks = listOf(block.copy(uri = "file:///old/video.mp4", thumbnailUri = null, caption = "Recovered caption")),
                )
            val result =
                useCase.restore(
                    buildBundle(drafts = listOf(incoming), mediaManifest = ExportMediaManifest(emptyList())),
                    mediaImporter =
                        object : MediaImporter {
                            override suspend fun importMedia(exportPath: String): String? = null

                            override suspend fun isAvailable(reference: String) = reference.startsWith("file:///accepted/")
                        },
                )
            assertEquals(
                block.copy(caption = "Recovered caption"),
                journalRepo.savedDrafts
                    .single()
                    .blocks
                    .single(),
            )
            assertTrue(result.unavailableMedia.isEmpty())
        }

    private suspend fun assertExistingOriginalSurvives(includeMedia: Boolean) {
        val id = Uuid.random()
        val existing = JournalNote.Image(id, now - 2.days, now - 1.days, "file:///accepted/photo.jpg", caption = "Old caption")
        notesRepo.existingNotes[id] = existing
        val incoming =
            ExportNote(
                id.toString(),
                "image",
                mediaPath = "file:///old-device/photo.jpg",
                caption = "Recovered caption",
                createdAt = existing.creationTimestamp,
                updatedAt = now,
            )
        val result =
            useCase.restore(
                buildBundle(notes = listOf(incoming), mediaManifest = ExportMediaManifest(emptyList())),
                options = RestoreOptions(includeMedia = includeMedia),
                mediaImporter =
                    object : MediaImporter {
                        override suspend fun importMedia(exportPath: String): String? = null

                        override suspend fun isAvailable(reference: String): Boolean = reference == existing.mediaRef
                    },
            )
        val saved = assertIs<JournalNote.Image>(notesRepo.existingNotes[id])
        assertEquals(existing.mediaRef, saved.mediaRef)
        assertEquals("Recovered caption", saved.caption)
        assertTrue(notesRepo.removed.isEmpty())
        assertTrue(result.unavailableMedia.isEmpty())
        assertEquals(1, result.notesImported)
    }
}
