package app.logdate.client.domain.restore

import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveJson
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaRef
import app.logdate.client.domain.export.archive.ArchiveMediaStatus
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.support.ArchiveSamples
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class ArchiveMediaAdmissionRestoreTest : RestoreArchiveTestFixture() {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `corrupt incoming bytes do not replace a readable destination original`() = runTest { restore(true) }

    @Test
    fun `corrupt incoming bytes without a local original remain pending with expected checksum`() = runTest { restore(false) }

    private suspend fun restore(hasOriginal: Boolean) {
        val id = Uuid.random()
        val original = "file:///accepted/original.png"
        if (hasOriginal) {
            notesRepo.existingNotes[id] =
                JournalNote.Image(
                    uid = id,
                    mediaRef = original,
                    creationTimestamp = now - 1.days,
                    lastUpdated = now,
                )
        }
        val files = archiveIntegrityFixture()
        val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), files.getValue("manifest.json").decodeToString())
        val note =
            ArchiveSamples.note.copy(
                id = id.toString(),
                createdAt = now - 1.days,
                updatedAt = now + 1.days,
                media = ArchiveMediaRef(ArchiveMediaStatus.INCLUDED, ArchivePath.of("media/original.bin"), "image/png"),
                location = null,
                locationDetails = null,
                journalIds = emptyList(),
            )
        files["manifest.json"] =
            ArchiveJson.document
                .encodeToString(
                    ArchiveManifest.serializer(),
                    manifest.copy(
                        contents =
                            manifest.contents + ArchiveContent(ArchiveRole.NOTES, ArchivePath.of("data/notes.json"), "application/json"),
                    ),
                ).encodeToByteArray()
        files["data/notes.json"] =
            ArchiveJson.document
                .encodeToString(ArchiveNoteFile.serializer(), ArchiveNoteFile(listOf(note)))
                .encodeToByteArray()
        sealArchiveIntegrityFixture(files)
        files["media/original.bin"] = "abd".encodeToByteArray()
        val archive = RestoreArchiveReader.readBytes(files.keys, files::get)
        val index = ArchiveRecoveryMediaIndex(archive)
        val pending = mutableListOf<UnavailableMediaAsset>()
        var published = 0
        val importer =
            object : MediaImporter {
                override suspend fun importMedia(exportPath: String): String? =
                    try {
                        ArchiveMediaIntegrity(3, abc).copyTo(Buffer().write(files.getValue(exportPath)), Buffer())
                        published++
                        "file:///should-not-publish.png"
                    } catch (failure: IllegalArgumentException) {
                        null
                    }

                override suspend fun isAvailable(reference: String) = hasOriginal && reference == original

                override fun containsMedia(exportPath: String) = exportPath in files
            }
        useCase.restore(archive, mediaImporter = importer, onPendingMedia = {
            pending += it.copy(expectedSha256 = index.expectedChecksum(it))
        })
        assertEquals(0, published)
        val saved = notesRepo.existingNotes.getValue(id) as JournalNote.Image
        if (hasOriginal) {
            assertEquals(original, saved.mediaRef)
            assertTrue(pending.isEmpty())
        } else {
            assertTrue(saved.mediaRef.startsWith("logdate-unavailable://archive/"))
            assertEquals(abc, pending.single().expectedSha256)
        }
    }
}
