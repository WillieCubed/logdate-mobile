package app.logdate.client.domain.restore.archive3

import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.StagingDirectoryArchiveContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class NativeArchive3WriterTest {
    @Test
    fun `populated category without receiver support cannot claim a complete transfer`() {
        val parent = Files.createTempDirectory("logdate-archive3-categories").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            val source =
                NativeArchive3ExportSource(
                    exportedAt = "2026-10-23T00:00:00Z",
                    journals = emptyList(),
                    notes = Json.parseToJsonElement("[]").jsonArray,
                    drafts = Json.parseToJsonElement("[]").jsonArray,
                    media = emptyList(),
                    unsupportedPopulatedCategories = setOf("places", "locationHistory"),
                )
            assertFailsWith<IllegalArgumentException> {
                NativeArchive3Writer.write(source, StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root))
            }
            assertFalse(FileSystem.SYSTEM.exists(root))
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `complete source preserves places profile and location history`() {
        val parent = Files.createTempDirectory("logdate-archive3-auxiliary").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            val place =
                buildJsonObject {
                    put("id", "33333333-3333-3333-3333-333333333333")
                    put("name", "Home")
                    put("latitude", 36.1)
                    put("longitude", -115.1)
                    put("radiusMeters", 100.0)
                }
            val profile = buildJsonObject { put("displayName", "A person") }
            val sample =
                buildJsonObject {
                    put("timestamp", "2026-10-23T00:00:00Z")
                    put("loggedAt", "2026-10-23T00:00:01Z")
                    put("latitude", 36.1)
                    put("longitude", -115.1)
                    put("altitudeMeters", 0.0)
                    put("confidence", 1.0)
                    put("isGenuine", true)
                    put("isMock", false)
                    put("capturePipeline", "android")
                    put("captureSource", "device")
                }
            val source =
                NativeArchive3ExportSource(
                    exportedAt = "2026-10-23T00:00:00Z",
                    journals = emptyList(),
                    notes = Json.parseToJsonElement("[]").jsonArray,
                    drafts = Json.parseToJsonElement("[]").jsonArray,
                    media = emptyList(),
                    places = JsonArray(listOf(place)),
                    profile = profile,
                    locationSamples = listOf(sample),
                )
            val admitted = NativeArchive3Writer.write(source, StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root))
            assertEquals(1, admitted.places.size)
            assertEquals(profile, admitted.profile)
            assertEquals(listOf(sample), admitted.locationSamples)
            val manifest = Json.parseToJsonElement(FileSystem.SYSTEM.read(root / "manifest.json") { readUtf8() }).jsonObject
            val history =
                manifest
                    .getValue("contents")
                    .jsonArray
                    .single {
                        it.jsonObject
                            .getValue("role")
                            .jsonPrimitive.content == "locationHistory"
                    }.jsonObject
            assertEquals("data/location-history.jsonl", history.getValue("path").jsonPrimitive.content)
            assertEquals("text/plain", history.getValue("mediaType").jsonPrimitive.content)
            assertEquals("schema/location-sample.schema.json", history.getValue("schema").jsonPrimitive.content)
            System.getenv("LOGDATE_NATIVE3_FIXTURE_DIR")?.let { destination ->
                Files.move(
                    parent.resolve("archive").toPath(),
                    java.nio.file.Path
                        .of(destination),
                )
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `complete source writes a receiver-admitted archive with original media`() {
        val parent = Files.createTempDirectory("logdate-archive3-writer").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            val path = ArchivePath.of("media/notes/22222222-2222-2222-2222-222222222222/original")
            val bytes = ByteArray(32769) { (it % 251).toByte() }
            val noteDocument =
                Json.parseToJsonElement(
                    """{"notes":[{"id":"22222222-2222-2222-2222-222222222222","type":"image","createdAt":"2026-10-23T00:00:00Z","updatedAt":null,"caption":"Original caption","journalIds":[],"nativePrimaryJournalId":null,"media":{"status":"included","path":"$path","mediaType":"image/png"}}]}""",
                )
            val source =
                NativeArchive3ExportSource(
                    exportedAt = "2026-10-23T00:00:00Z",
                    journals = emptyList(),
                    notes = noteDocument.jsonObject.getValue("notes").jsonArray,
                    drafts = Json.parseToJsonElement("[]").jsonArray,
                    media =
                        listOf(
                            NativeArchive3MediaSource(path, "image/png", bytes.size.toLong(), bytes.toByteString().sha256().hex()) {
                                Buffer().write(bytes)
                            },
                        ),
                )
            val produced = NativeArchive3Writer.write(source, StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root))
            assertEquals(1, produced.notes.size)
            assertEquals(bytes.toList(), FileSystem.SYSTEM.read(root / path.value) { readByteArray().toList() })
            assertEquals(
                produced.manifestSha256,
                FileSystem.SYSTEM.read(root / "manifest.json") {
                    readByteArray()
                        .toByteString()
                        .sha256()
                        .hex()
                },
            )
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `changed original is rejected without publishing an archive`() {
        val parent = Files.createTempDirectory("logdate-archive3-changed").toFile()
        try {
            val root = parent.resolve("archive").absolutePath.toPath()
            val source =
                NativeArchive3ExportSource(
                    "2026-10-23T00:00:00Z",
                    emptyList(),
                    Json.parseToJsonElement("[]").jsonArray,
                    Json.parseToJsonElement("[]").jsonArray,
                    listOf(
                        NativeArchive3MediaSource(
                            ArchivePath.of("media/notes/22222222-2222-2222-2222-222222222222/original"),
                            "image/png",
                            3,
                            "0".repeat(64),
                        ) { Buffer().writeUtf8("bad") },
                    ),
                )
            assertFailsWith<IllegalArgumentException> {
                NativeArchive3Writer.write(source, StagingDirectoryArchiveContainer(FileSystem.SYSTEM, root))
            }
            assertFalse(FileSystem.SYSTEM.exists(root))
        } finally {
            parent.deleteRecursively()
        }
    }
}
