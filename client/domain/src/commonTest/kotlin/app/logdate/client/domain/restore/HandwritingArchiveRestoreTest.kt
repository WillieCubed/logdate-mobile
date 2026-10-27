package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveBlockType
import app.logdate.client.domain.export.archive.ArchiveDraft
import app.logdate.client.domain.export.archive.ArchiveDraftBlock
import app.logdate.client.domain.export.archive.ArchiveDraftFile
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveNote
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class HandwritingArchiveRestoreTest {
    @Test
    fun inkSavedNoteRestoresWithoutRequiringAMediaAttachment() =
        runTest {
            val time = Instant.fromEpochMilliseconds(1000)
            val id = Uuid.random()
            val ink =
                HandwritingDocument(
                    strokes =
                        listOf(
                            HandwritingStroke(
                                Uuid.random(),
                                "pen",
                                "#263044",
                                3.5f,
                                listOf(HandwritingPoint(12.5f, 400.75f, 0.3f)),
                            ),
                        ),
                )
            val notes =
                ArchiveNoteFile(
                    listOf(
                        ArchiveNote(
                            id = id.toString(),
                            type = ArchiveNoteType.HANDWRITING,
                            createdAt = time,
                            updatedAt = time,
                            handwriting = ink,
                        ),
                    ),
                )
            val json = ArchiveJson.document
            val adapted =
                V2RestoreBundle(
                    manifestJson = json.encodeToString(ArchiveManifest.serializer(), ArchiveSamples.manifest),
                    notesJson = json.encodeToString(ArchiveNoteFile.serializer(), notes),
                ).adaptForRestore("owner", "device")
            val note = json.decodeFromString(NotesPayload.serializer(), adapted.legacy.notesJson).notes.single()

            assertNull(note.mediaPath, "Original ink is embedded content and must not acquire a missing-media reference")
            assertTrue(adapted.warnings.isEmpty())
            val resolution = RestoreMediaResolver(manifest = null, importer = null, includeMedia = true).resolve(note.mediaPath)
            assertNull(resolution.unavailableReason)
            assertNull(resolution.unavailableAsset(note.id, kind = note.type))
            val restored = assertIs<JournalNote.Handwriting>(note.toJournalNote(id, resolution))
            assertEquals(ink, restored.document)
        }

    @Test
    fun inkDraftRestoresWithoutRequiringAMediaAttachment() {
        val time = Instant.fromEpochMilliseconds(1000)
        val ink =
            HandwritingDocument(
                strokes =
                    listOf(
                        HandwritingStroke(
                            Uuid.random(),
                            "pen",
                            "#263044",
                            3.5f,
                            listOf(HandwritingPoint(12.5f, 400.75f, 0.3f)),
                        ),
                    ),
            )
        val drafts =
            ArchiveDraftFile(
                listOf(
                    ArchiveDraft(
                        id = Uuid.random().toString(),
                        createdAt = time,
                        updatedAt = time,
                        blocks =
                            listOf(
                                ArchiveDraftBlock(
                                    id = Uuid.random().toString(),
                                    type = ArchiveBlockType.HANDWRITING,
                                    timestamp = time,
                                    handwriting = ink,
                                ),
                            ),
                    ),
                ),
            )
        val json = ArchiveJson.document
        val adapted =
            V2RestoreBundle(
                manifestJson = json.encodeToString(ArchiveManifest.serializer(), ArchiveSamples.manifest),
                draftsJson = json.encodeToString(ArchiveDraftFile.serializer(), drafts),
            ).adaptForRestore("owner", "device")
        val blocks =
            json
                .parseToJsonElement(adapted.legacy.draftsJson)
                .jsonObject
                .getValue("drafts")
                .jsonArray
                .single()
                .jsonObject
                .getValue("blocks")
                .jsonArray
        assertEquals(1, blocks.size)
        assertEquals(ink, json.decodeFromJsonElement(HandwritingDocument.serializer(), blocks.single().jsonObject.getValue("document")))
    }
}
