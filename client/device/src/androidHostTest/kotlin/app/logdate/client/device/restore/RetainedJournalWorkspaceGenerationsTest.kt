package app.logdate.client.device.restore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class RetainedJournalWorkspaceGenerationsTest {
    @Test
    fun `second generation transfer rejects a corrupted first original`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            val second = fixture.store().prepare()
            assertNotEquals(first.id, second.id)
            assertNotEquals(first.primaryKeyName, second.primaryKeyName)
            fixture.database.writeText("Third working journal")
            val key = File(fixture.root, "working-key").apply { writeText("Third key") }
            File(fixture.retained, "${first.id}/database/logdate-wal").appendBytes(byteArrayOf(99))
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, key) }
        }
    }

    @Test
    fun `second generation transfer rejects a missing first original`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            fixture.store().prepare()
            fixture.database.writeText("Third working journal")
            val key = File(fixture.root, "working-key").apply { writeText("Third key") }
            check(File(fixture.retained, "${first.id}/database/logdate").delete())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
        }
    }

    @Test
    fun `a rewritten first manifest cannot authenticate rewritten original bytes`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            fixture.store().prepare()
            fixture.database.writeText("Third working journal")
            val key = File(fixture.root, "working-key").apply { writeText("Third key") }
            val original = File(fixture.retained, "${first.id}/database/logdate")

            fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val oldHash = hash(original.readBytes())
            original.writeBytes(ByteArray(original.length().toInt()) { 42 })
            val manifest = File(fixture.retained, "${first.id}/manifest.json")
            manifest.writeText(manifest.readText().replace(oldHash, hash(original.readBytes())))
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
        }
    }

    @Test
    fun `all generations remain anchored through every second preparation interruption`() {
        val steps = mutableListOf<String>()
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            fixture.store { steps += it }.prepare()
        }
        for (failure in steps.distinct()) {
            RetainedJournalWorkspaceFixture().use { fixture ->
                val first = fixture.store().prepare()
                fixture.database.writeText("Second encrypted journal")
                var fired = false
                assertFailsWith<IOException> {
                    fixture
                        .store { step ->
                            if (!fired && step == failure) {
                                fired = true
                                throw IOException("Interrupted second preparation")
                            }
                        }.prepare()
                }
                val second = fixture.store().resume()!!
                assertEquals(RetainedJournalWorkspace.Phase.WORKING, second.phase)
                fixture.assertRetained(first)
                fixture.database.writeText("Third working journal")
                val key = File(fixture.root, "working-key").apply { writeText("Third key") }
                fixture.store().requireBackupReady(true, fixture.database, key)
                File(fixture.retained, "${first.id}/database/logdate-wal").appendBytes(byteArrayOf(99))
                assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
                assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, key) }
            }
        }
    }

    @Test
    fun `intact generations transfer without local receipts and keep their original manifest anchors`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            val second = fixture.store().prepare()
            fixture.database.writeText("Third working journal")
            val destination = File(fixture.root, "multi-destination").apply { mkdirs() }
            val retained = File(destination, "retained")
            fixture.retained.copyRecursively(retained)
            File(fixture.root, "session.json").copyTo(File(destination, "session.json"))
            val working = fixture.database.copyTo(File(destination, "logdate"))
            val key = File(destination, "working-key").apply { writeText("Third portable key") }
            val received =
                RetainedJournalWorkspace(
                    File(destination, "session.json"),
                    retained,
                    { emptyMap() },
                    fixture
                        .sources()
                        .keys
                        .filter { it.startsWith("database/") }
                        .toSet(),
                    File(destination, "no-backup/authority"),
                )
            assertFalse(File(destination, "no-backup").exists())
            assertEquals(second, received.resume())
            received.requireBackupReady(true, working, key)
            assertFailsWith<IllegalStateException> { received.requireBackupReady(false, working, key) }
            check(File(retained, "${first.id}/database/logdate-wal").delete())
            assertFailsWith<IllegalStateException> { received.requireBackupReady(true, working, key) }
            assertEquals("Third working journal", working.readText())
        }
    }

    @Test
    fun `third generation still authenticates the first original`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val first = fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            fixture.store().prepare()
            fixture.database.writeText("Third encrypted journal")
            fixture.store().prepare()
            fixture.database.writeText("Fourth working journal")
            val key = File(fixture.root, "working-key").apply { writeText("Fourth key") }
            fixture.store().requireBackupReady(true, fixture.database, key)
            File(fixture.retained, "${first.id}/database/logdate-wal").appendBytes(byteArrayOf(99))
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, key) }
        }
    }

    @Test
    fun `portable state cannot drop earlier anchors committed into the latest manifest`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().prepare()
            fixture.database.writeText("Second encrypted journal")
            fixture.store().prepare()
            fixture.database.writeText("Third working journal")
            val key = File(fixture.root, "working-key").apply { writeText("Third key") }
            val state = File(fixture.root, "session.json")
            val changed = Json.parseToJsonElement(state.readText()).jsonObject.toMutableMap()
            changed["priorGenerations"] = JsonObject(emptyMap())
            state.writeText(JsonObject(changed).toString())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, key) }
        }
    }
}
