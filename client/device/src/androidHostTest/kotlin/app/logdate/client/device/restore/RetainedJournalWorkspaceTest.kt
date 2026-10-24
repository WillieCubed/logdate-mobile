package app.logdate.client.device.restore

import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RetainedJournalWorkspaceTest {
    @Test
    fun `a working journal is enabled only after every retained byte is committed`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            assertEquals(RetainedJournalWorkspace.Phase.WORKING, session.phase)
            assertNotEquals("db_encryption_key", session.primaryKeyName)
            assertFalse(fixture.database.exists())
            fixture.assertRetained(session)
            fixture.keys.forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
        }
    }

    @Test
    fun `unreadable source scope keeps the original journal and retries before detachment`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val anchor = File(fixture.root, "source-scope.json").apply { writeText("prior anchor") }
            val authority = File(fixture.root, "no-backup/authority")
            val state = File(fixture.root, "session.json")

            fun workspace(read: () -> androidx.datastore.preferences.core.Preferences) =
                RetainedJournalWorkspace(
                    state,
                    fixture.retained,
                    { fixture.sources() + ("source_scope.json" to anchor) },
                    fixture.sources().keys.filterTo(mutableSetOf()) { it.startsWith("database/") },
                    authority,
                    beforePrepare = { captureRetainedSourceScope(anchor, read) },
                )

            assertFailsWith<IOException> { workspace { throw IOException("Preferences temporarily unavailable") }.prepare() }
            fixture.sources().forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
            assertEquals("prior anchor", anchor.readText())
            assertFalse(state.exists())
            assertFalse(authority.exists())
            assertFalse(fixture.retained.exists())

            val session = workspace { mutablePreferencesOf() }.prepare()
            assertEquals(RetainedJournalWorkspace.Phase.WORKING, session.phase)
            assertTrue(File(fixture.retained, "${session.id}/source_scope.json").readText().contains("\"captured\""))
            fixture.assertRetained(session)
        }
    }

    @Test
    fun `every durable interruption resumes without losing the retained database or keys`() {
        val steps = mutableListOf<String>()
        RetainedJournalWorkspaceFixture().use { it.store { step -> steps += step }.prepare() }
        for (failure in steps.distinct()) {
            RetainedJournalWorkspaceFixture().use { fixture ->
                var fired = false
                assertFailsWith<IOException> {
                    fixture
                        .store { step ->
                            if (!fired && step == failure) {
                                fired = true
                                throw IOException("Controlled durability interruption")
                            }
                        }.prepare()
                }
                val resumed = fixture.store().resume()!!
                assertEquals(RetainedJournalWorkspace.Phase.WORKING, resumed.phase)
                fixture.assertRetained(resumed)
                fixture.keys.forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
            }
        }
    }

    @Test
    fun `copy failure keeps the original database and every sidecar unchanged`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            assertFailsWith<IOException> {
                fixture.store { if (it.startsWith("copied:")) throw IOException("Controlled full disk") }.prepare()
            }
            fixture.sources().forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
            assertEquals(RetainedJournalWorkspace.Phase.PREPARING, fixture.store().current()!!.phase)
        }
    }

    @Test
    fun `a changed original cannot be detached using a stale retained snapshot`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            assertFailsWith<IOException> {
                fixture
                    .store {
                        if (it == "snapshot-committed") throw IOException("Controlled interruption")
                    }.prepare()
            }
            fixture.database.appendBytes(byteArrayOf(99))
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            assertTrue(fixture.database.exists())
            assertEquals(RetainedJournalWorkspace.Phase.DETACHING, fixture.store().current()!!.phase)
        }
    }

    @Test
    fun `a corrupt retained copy cannot authorize deletion of another original`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            assertFailsWith<IOException> {
                fixture.store { if (it == "detaching-committed") throw IOException("Controlled interruption") }.prepare()
            }
            val session = fixture.store().current()!!
            File(fixture.retained, "${session.id}/database/logdate-wal").appendBytes(byteArrayOf(99))
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            fixture.sources().forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
        }
    }

    @Test
    fun `restart after working activation never detaches new journal data`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val accepted = fixture.store().prepare()
            fixture.database.writeBytes(byteArrayOf(7, 8, 9))
            assertEquals(accepted, fixture.store().resume())
            assertContentEquals(byteArrayOf(7, 8, 9), fixture.database.readBytes())
            fixture.assertRetained(accepted)
        }
    }

    @Test
    fun `a transferred transition marker cannot authorize local database detachment`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            assertFailsWith<IOException> {
                fixture.store { if (it == "detaching-committed") throw IOException("Controlled interruption") }.prepare()
            }
            check(File(fixture.root, "no-backup/authority").delete())
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            fixture.sources().forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
        }
    }

    @Test
    fun `new recovery material during copying is retained by the same retry`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val extra = File(fixture.root, "keys/new-recovery-material")

            fun sources() = fixture.sources() + if (extra.exists()) mapOf("keys/new-recovery-material" to extra) else emptyMap()

            fun store(step: (String) -> Unit = {}) =
                RetainedJournalWorkspace(
                    File(fixture.root, "session.json"),
                    fixture.retained,
                    ::sources,
                    setOf("database/logdate", "database/logdate-wal", "database/logdate-shm", "database/logdate-journal"),
                    File(fixture.root, "no-backup/authority"),
                    afterDurableStep = step,
                )
            assertFailsWith<IllegalStateException> {
                store { if (it.startsWith("copied:") && !extra.exists()) extra.writeText("Newly delivered recovery material") }.prepare()
            }
            fixture.sources().forEach { (name, file) -> assertContentEquals(fixture.before.getValue(name), file.readBytes()) }
            val session = store().resume()!!
            fixture.assertRetained(session)
            assertEquals(extra.readText(), File(fixture.retained, "${session.id}/keys/new-recovery-material").readText())
        }
    }

    @Test
    fun `an unrelated working journal never releases retained cloud backup custody`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("A tiny unrelated imported journal")
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, workingKey) }
            fixture.assertRetained(session)
        }
    }

    @Test
    fun `working content without imported media cannot replace the retained cloud backup`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("Working entries after includeMedia=false")
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, workingKey) }
            fixture.assertRetained(session)
        }
    }

    @Test
    fun `working content with missing originals cannot replace the retained cloud backup after restart`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("Working entries with originals still pending")
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            assertEquals(session, fixture.store().resume())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, workingKey) }
            fixture.assertRetained(session)
        }
    }

    @Test
    fun `direct transfer preserves verified retained custody and independent working content`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("Usable independent working content")
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            fixture.store().requireBackupReady(true, fixture.database, workingKey)
            assertEquals(session, fixture.store().current())
            fixture.assertRetained(session)
            assertEquals("Usable independent working content", fixture.database.readText())
            assertEquals("Independent key", workingKey.readText())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(false, fixture.database, workingKey) }
        }
    }

    @Test
    fun `direct transfer rejects missing or corrupt retained bytes without changing working content`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("Working content")
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            val retained = File(fixture.retained, "${session.id}/database/logdate-wal")
            retained.appendBytes(byteArrayOf(99))
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, workingKey) }
            check(retained.delete())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, workingKey) }
            assertEquals("Working content", fixture.database.readText())
        }
    }

    @Test
    fun `direct transfer requires a working key and rejects an unfinished retention transaction`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            assertFailsWith<IOException> {
                fixture.store { if (it == "detaching-committed") throw IOException("Controlled interruption") }.prepare()
            }
            val workingKey = File(fixture.root, "working-key").apply { writeText("Independent key") }
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, workingKey) }
            val session = fixture.store().resume()!!
            fixture.database.writeText("Working content")
            check(workingKey.delete())
            assertFailsWith<IllegalStateException> { fixture.store().requireBackupReady(true, fixture.database, workingKey) }
            fixture.assertRetained(session)
        }
    }

    @Test
    fun `a transferred working generation keeps custody without local content receipts or authority`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            val session = fixture.store().prepare()
            fixture.database.writeText("Working journal after partial import")
            val destination = File(fixture.root, "destination").apply { mkdirs() }
            val retained = File(destination, "retained")
            fixture.retained.copyRecursively(retained)
            File(fixture.root, "session.json").copyTo(File(destination, "session.json"))
            val working = fixture.database.copyTo(File(destination, "logdate"))
            val workingKey = File(destination, "working-key").apply { writeText("Portable working key") }
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
            assertEquals(session, received.resume())
            received.requireBackupReady(true, working, workingKey)
            assertFailsWith<IllegalStateException> { received.requireBackupReady(false, working, workingKey) }
            assertEquals(session, received.current())
            fixture.before.forEach { (name, bytes) -> assertContentEquals(bytes, File(retained, "${session.id}/$name").readBytes()) }
        }
    }

    @Test
    fun `journals without a retained generation keep the existing platform backup policy`() {
        RetainedJournalWorkspaceFixture().use { fixture ->
            fixture.store().requireBackupReady(false, fixture.database, File(fixture.root, "unused-key"))
            fixture.store().requireBackupReady(true, fixture.database, File(fixture.root, "unused-key"))
        }
    }
}
