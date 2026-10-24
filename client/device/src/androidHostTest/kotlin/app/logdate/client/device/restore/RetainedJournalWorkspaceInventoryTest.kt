package app.logdate.client.device.restore

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedJournalWorkspaceInventoryTest {
    @Test
    fun `five thousand original media files have bounded immutable records and a compact session`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val padding = List(4) { "p".repeat(80) }.joinToString("/")
            val media =
                (0 until 5001).associate { index ->
                    "files/media/$padding/$index.jpg" to
                        File(fixture.root, "original-media/$index.jpg").apply {
                            checkNotNull(parentFile).mkdirs()
                            writeBytes(byteArrayOf(7))
                        }
                }
            val store =
                RetainedJournalWorkspace(
                    File(fixture.root, "session.json"),
                    fixture.retained,
                    { fixture.sources() + media },
                    fixture
                        .sources()
                        .keys
                        .filter { it.startsWith("database/") }
                        .toSet(),
                    File(fixture.root, "no-backup/authority"),
                )
            val session = store.prepare()
            assertTrue(File(fixture.root, "session.json").length() < 65536)
            assertEquals(
                5001,
                store
                    .retainedSources()
                    .getValue(session.id)
                    .keys
                    .count { it.startsWith("files/media/") },
            )
            val records = File(fixture.retained, "${session.id}/.inventory").listFiles()!!
            assertTrue(records.sumOf(File::length) > 1024 * 1024)
            assertTrue(records.all { it.length() <= 8192 })
            media.forEach { (path, file) ->
                assertContentEquals(file.readBytes(), File(fixture.retained, "${session.id}/$path").readBytes())
            }
        }
    }

    @Test
    fun `long source paths do not inflate second session or lose its earlier anchors`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            val padding = List(4) { "p".repeat(160) }.joinToString("/")
            val extra = (0 until 110).associate { "prefs/$padding/$it" to fixture.keys.values.first() }
            val store =
                RetainedJournalWorkspace(
                    File(fixture.root, "session.json"),
                    fixture.retained,
                    { fixture.sources() + extra },
                    fixture
                        .sources()
                        .keys
                        .filter { it.startsWith("database/") }
                        .toSet(),
                    File(fixture.root, "no-backup/authority"),
                )
            val second = store.prepare()
            assertEquals(first.manifestSha256, second.priorGenerations[first.id])
            assertTrue(File(fixture.root, "session.json").length() < 65536)
            fixture.assertRetained(first)
            assertEquals(
                110,
                store
                    .retainedSources()
                    .getValue(second.id)
                    .keys
                    .count { it.startsWith("prefs/") },
            )
        }
    }

    @Test
    fun `Unicode source paths live in bounded records instead of the portable session`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val padding = List(3) { "é".repeat(100) }.joinToString("/")
            val extra = (0 until 110).associate { "prefs/$padding/$it" to fixture.keys.values.first() }
            val store =
                RetainedJournalWorkspace(
                    File(fixture.root, "session.json"),
                    fixture.retained,
                    { fixture.sources() + extra },
                    fixture
                        .sources()
                        .keys
                        .filter { it.startsWith("database/") }
                        .toSet(),
                    File(fixture.root, "no-backup/authority"),
                )
            val session = store.prepare()
            assertTrue(File(fixture.root, "session.json").length() < 65536)
            assertEquals(
                110,
                store
                    .retainedSources()
                    .getValue(session.id)
                    .keys
                    .count { it.startsWith("prefs/") },
            )
        }
    }

    @Test
    fun `new generation state overflow preserves previous authority marker and working originals`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture
                .sources()
                .filterKeys { it.startsWith("database/") }
                .values
                .forEach { it.writeText("accepted working bytes") }
            val cohorts = mutableMapOf<String, String>()
            var previous = first
            while (true) {
                cohorts[
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                ] = "a".repeat(64)
                val proposed = first.copy(incomingCohorts = cohorts.toMap())
                if (Json.encodeToString(RetainedJournalWorkspace.Session.serializer(), proposed).encodeToByteArray().size > 65536) break
                previous = proposed
            }
            val marker = File(fixture.root, "session.json")
            marker.writeText(Json.encodeToString(RetainedJournalWorkspace.Session.serializer(), previous))
            val before = marker.readBytes()
            val authority = File(fixture.root, "no-backup/authority").readBytes()
            assertFailsWith<IllegalStateException> { fixture.store().prepare() }
            assertContentEquals(before, marker.readBytes())
            assertContentEquals(authority, File(fixture.root, "no-backup/authority").readBytes())
            assertEquals("accepted working bytes", fixture.database.readText())
            fixture.assertRetained(first)
        }
    }

    @Test
    fun `late incoming database cannot change the working journal and pending bytes block transfer`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().prepare()
            fixture.database.writeText("accepted working entry")
            val key = fixture.keys.values.first()
            val store = fixture.store()
            val id =
                store.beginIncomingDelivery(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.captureIncomingFile(id, "database/logdate", 3, "old".byteInputStream())
            assertEquals("accepted working entry", fixture.database.readText())
            assertFailsWith<IllegalStateException> { store.requireBackupReady(true, fixture.database, key) }
            fixture.store().finishIncomingDelivery()
            fixture.store().requireBackupReady(true, fixture.database, key)
            assertEquals(
                "old",
                fixture
                    .store()
                    .incomingPayloads()
                    .getValue(id)
                    .single()
                    .file
                    .readText(),
            )
            assertFailsWith<IllegalStateException> { store.requireBackupReady(false, fixture.database, key) }
        }
    }

    @Test
    fun `second generation keeps incoming anchors and verifies every staged original`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().prepare()
            fixture.database.writeText("working entry")
            val store = fixture.store()
            val id =
                store.beginIncomingDelivery(
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                )
            store.captureIncomingFile(id, "files/db_recovery/passphrase_backup", 3, "key".byteInputStream())
            store.finishIncomingDelivery()
            val first = store.current()!!
            val second = fixture.store().prepare()
            assertEquals(first.incomingCohorts, second.incomingCohorts)
            fixture.database.writeText("newer working entry")
            fixture.store().requireBackupReady(true, fixture.database, fixture.keys.values.first())
            fixture
                .store()
                .incomingPayloads()
                .getValue(id)
                .single()
                .file
                .appendText("corrupt")
            assertFailsWith<IllegalStateException> {
                fixture.store().requireBackupReady(true, fixture.database, fixture.keys.values.first())
            }
        }
    }

    @Test
    fun `portable working marker alone cannot intercept a fresh destination restore`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().prepare()
            assertTrue(fixture.store().canStageNativeDelivery())
            val destination = File(fixture.root, "destination").apply { mkdirs() }
            File(fixture.root, "session.json").copyTo(File(destination, "session.json"))
            fixture.retained.copyRecursively(File(destination, "retained"))
            val transferred =
                RetainedJournalWorkspace(
                    File(destination, "session.json"),
                    File(destination, "retained"),
                    { emptyMap() },
                    setOf("database/logdate"),
                    File(destination, "no-backup/authority"),
                )
            assertFalse(transferred.canStageNativeDelivery())
            transferred.recordWorkingJournalOpened()
            assertTrue(transferred.canStageNativeDelivery())
        }
    }

    @Test
    fun `source scope is captured before detach and stays immutable across later owner changes`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val scope = File(fixture.root, "source-scope.json")
            var effectiveOrigin = "https://original.example"
            val store =
                RetainedJournalWorkspace(
                    File(fixture.root, "session.json"),
                    fixture.retained,
                    { fixture.sources() + mapOf("source_scope.json" to scope) },
                    fixture
                        .sources()
                        .keys
                        .filter { it.startsWith("database/") }
                        .toSet(),
                    File(fixture.root, "no-backup/authority"),
                    beforePrepare = {
                        assertTrue(fixture.database.exists())
                        scope.writeText(effectiveOrigin)
                    },
                )
            val first = store.prepare()
            fixture.database.writeText("new working journal")
            effectiveOrigin = "https://later.example"
            val second = store.prepare()
            val retained = store.retainedSources()
            assertEquals("https://original.example", retained.getValue(first.id).getValue("source_scope.json").readText())
            assertEquals("https://later.example", retained.getValue(second.id).getValue("source_scope.json").readText())
        }
    }
}
