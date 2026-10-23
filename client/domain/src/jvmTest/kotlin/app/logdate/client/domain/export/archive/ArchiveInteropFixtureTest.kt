package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.archive.support.ArchiveExportFixture
import app.logdate.client.domain.export.archive.support.InMemoryArchiveContainer
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptSoundTag
import app.logdate.shared.model.profile.LogDateProfile
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import java.nio.file.Files as JvmFiles

/** A deterministic, synthetic archive produced by the real exporter for native consumer tests. */
class ArchiveInteropFixtureTest : ArchiveExportFixture() {
    @Test
    fun actualExporterProducesSelfValidatingInteropFixture() =
        runTest {
            val at = Instant.parse("2026-09-18T03:30:05.123456789Z")
            val updated = Instant.parse("2026-09-19T10:00:06.987654321Z")
            val first =
                daily.copy(
                    id = id(1),
                    description = "A **portable** journal.",
                    created = at,
                    lastUpdated = updated,
                    isFavorited = true,
                    coverImageUri = null,
                )
            val second = travel.copy(id = id(2), description = "", created = at, lastUpdated = updated)
            val text =
                textNote.copy(
                    uid = id(3),
                    creationTimestamp = at,
                    lastUpdated = updated,
                    location =
                        NoteLocation(
                            coordinates = NoteCoordinates(36.1699, -115.1398, 610.25, 3.5f),
                            place = NotePlace(id(7), "Home", 36.17, -115.14),
                        ),
                )
            val image = imageNote.copy(uid = id(4), creationTimestamp = at, lastUpdated = updated, caption = "")
            val audio =
                audioNote.copy(
                    uid = id(5),
                    creationTimestamp = at,
                    lastUpdated = updated,
                    transcription = "  Original words.\nKeep spacing.  ",
                    transcript =
                        TranscriptDocument.fromPlainText("Structured words").copy(
                            revision = 4,
                            soundTags = listOf(TranscriptSoundTag("rain", 500, 1_000, 0.9f)),
                            createdAt = at,
                            updatedAt = updated,
                        ),
                )
            val video = videoNote.copy(uid = id(6), creationTimestamp = at, lastUpdated = updated, durationMs = 12_345, caption = "")
            journalsRepository.testJournals = listOf(first, second)
            journalsRepository.testDrafts = emptyList()
            notesRepository.testNotes = listOf(text, image, audio, video)
            notesRepository.notesByJournal = mapOf(first.id to listOf(text, image, audio), second.id to listOf(text, video))
            profileRepository.profile = LogDateProfile()
            placesRepository.places = emptyList()
            locationRepository.entries = emptyList()
            val png =
                assertNotNull(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGMIyFvwHwAEzgJeJi9oeAAAAABJRU5ErkJggg==".decodeBase64(),
                ).toByteArray()
            val wav = silentWav()
            val archive = InMemoryArchiveContainer()
            val progress =
                useCase(opener = Files(mapOf(imageReference to png, audioReference to wav)))
                    .export(ArchiveExportOptions(), archive)
                    .toList()
            assertIs<ArchiveExportProgress.Completed>(progress.last())
            val manifest = ArchiveJson.document.decodeFromString(ArchiveManifest.serializer(), archive.text("manifest.json"))
            assertEquals(ExportSchemaVersion.V2_1, manifest.schemaVersion)
            assertEquals(2, manifest.counts.journals)
            assertEquals(4, manifest.counts.notes)
            assertEquals(2, manifest.counts.media)
            assertFalse(manifest.scope.complete, "An omitted video and unsupported categories cannot claim complete system transfer")
            val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            assertEquals(
                emptyList(),
                registry
                    .getSchema(archive.text("schema/manifest.schema.json"))
                    .validate(archive.text("manifest.json"), InputFormat.JSON)
                    .map { it.message },
            )
            manifest.contents.filter { it.schema != null }.forEach { content ->
                val schema = registry.getSchema(archive.text(assertNotNull(content.schema).value))
                val records =
                    if (content.mediaType == ArchiveLayout.JSON_LINES_MEDIA_TYPE) {
                        archive.text(content.path.value).lines().filter { it.isNotBlank() }
                    } else {
                        listOf(archive.text(content.path.value))
                    }
                records.forEach { record ->
                    assertEquals(emptyList(), schema.validate(record, InputFormat.JSON).map { it.message }, content.path.value)
                }
            }
            val checksums = assertNotNull(ChecksumFile.parse(archive.text("SHA256SUMS")))
            assertEquals((archive.paths - "SHA256SUMS").toSet(), checksums.keys)
            checksums.forEach { (path, hash) ->
                assertEquals(
                    hash,
                    archive
                        .bytes(path)
                        .toByteString()
                        .sha256()
                        .hex(),
                    path,
                )
            }
            val notes = ArchiveJson.document.decodeFromString(ArchiveNoteFile.serializer(), archive.text("data/notes.json")).notes
            assertEquals("", notes.single { it.id == image.uid.toString() }.caption)
            assertEquals(null, notes.single { it.id == text.uid.toString() }.caption)
            assertEquals(audio.transcription, notes.single { it.id == audio.uid.toString() }.transcription)
            assertEquals(audio.transcript, notes.single { it.id == audio.uid.toString() }.transcript)
            assertEquals(video.durationMs, notes.single { it.id == video.uid.toString() }.durationMs)
            assertEquals(
                id(7).toString(),
                notes
                    .single { it.id == text.uid.toString() }
                    .locationDetails
                    ?.place
                    ?.id,
            )
            assertEquals(setOf(first.id.toString(), second.id.toString()), notes.single { it.id == text.uid.toString() }.journalIds.toSet())
            assertTrue(archive.paths.filter { it.startsWith("media/") }.any { it.endsWith(".wav") })
            System.getenv("LOGDATE_ARCHIVE_INTEROP_OUTPUT")?.let { output ->
                val root = Path.of(output)
                // A new output directory prevents an old file from masquerading as part of this export.
                JvmFiles.createDirectory(root)
                archive.paths.forEach { name ->
                    val target = root.resolve(name)
                    JvmFiles.createDirectories(target.parent)
                    JvmFiles.write(target, archive.bytes(name))
                }
            }
        }

    private fun id(value: Int) = Uuid.parse("00000000-0000-4000-8000-${value.toString().padStart(12, '0')}")

    private fun silentWav(): ByteArray =
        ByteBuffer
            .allocate(44 + 64_000)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".encodeToByteArray())
                putInt(36 + 64_000)
                put("WAVEfmt ".encodeToByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(8_000)
                putInt(16_000)
                putShort(2)
                putShort(16)
                put("data".encodeToByteArray())
                putInt(64_000)
            }.array()
}
