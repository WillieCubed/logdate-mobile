package app.logdate.client.device.restore

import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RetainedJournalWorkspaceAuthorityTest {
    private class Fixture : AutoCloseable {
        val root = Files.createTempDirectory("retained-authority-").toFile()
        val state = File(root, "state/session.json")
        val authority = File(root, "no-backup/authority")
        val accepted = File(authority.parentFile, ".retained_workspace_accepted")
        val retained = File(root, "retained")
        val database =
            File(root, "database/logdate").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("retained original")
            }

        fun store(step: (String) -> Unit = {}) =
            RetainedJournalWorkspace(
                state,
                retained,
                { mapOf("database/logdate" to database) },
                setOf("database/logdate"),
                authority,
                afterDurableStep = step,
            )

        fun firstWorking(): RetainedJournalWorkspace.Session {
            val store = store()
            val session = store.prepare()
            database.writeText("accepted working edits")
            store.recordWorkingJournalOpened()
            assertTrue(store.canStageNativeDelivery())
            assertFalse(accepted.exists())
            return session
        }

        override fun close() {
            check(root.deleteRecursively())
        }
    }

    @Test fun failingNewStateWriteKeepsPriorWorkingAuthorityAndNativeDeliveryStaged() =
        Fixture().use { fixture ->
            val first = fixture.firstWorking()
            val parent = checkNotNull(fixture.state.parentFile)
            val permissions = Files.getPosixFilePermissions(parent.toPath())
            Files.setPosixFilePermissions(parent.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))
            try {
                assertFalse(Files.isWritable(parent.toPath()))
                assertFailsWith<IOException> { fixture.store().prepare() }
            } finally {
                Files.setPosixFilePermissions(parent.toPath(), permissions)
            }
            assertNotEquals(first.id, fixture.authority.readText())
            val restarted = fixture.store()
            assertEquals(first.id, restarted.resume()?.id)
            assertTrue(
                restarted.canStageNativeDelivery(),
                "A failed second prepare must not route native delivery into the working journal",
            )
            val incoming = restarted.beginIncomingDelivery(Uuid.random().toString())
            val bytes = "late database source".encodeToByteArray()
            restarted.captureIncomingFile(incoming, "database/logdate", bytes.size.toLong(), bytes.inputStream())
            restarted.finishIncomingDelivery()
            assertEquals("accepted working edits", fixture.database.readText())
            assertEquals(
                "retained original",
                restarted
                    .retainedSources()
                    .getValue(first.id)
                    .getValue("database/logdate")
                    .readText(),
            )
            assertEquals(
                bytes.toList(),
                restarted
                    .incomingPayloads()
                    .getValue(incoming)
                    .single()
                    .file
                    .readBytes()
                    .toList(),
            )
        }

    @Test fun interruptionImmediatelyAfterNewAuthorityCommitRetainsLocalPriorGeneration() =
        Fixture().use { fixture ->
            val first = fixture.firstWorking()
            assertFailsWith<IOException> {
                fixture.store { if (it == "authority-committed") throw IOException("process interrupted after new authority") }.prepare()
            }
            assertNotEquals(first.id, fixture.authority.readText())
            assertEquals(first.id, fixture.store().current()?.id)
            assertTrue(fixture.store().canStageNativeDelivery())
            assertEquals("accepted working edits", fixture.database.readText())
        }

    @Test fun portableWorkingMarkerAndStaleLocalReceiptCannotCreatePriorAuthority() =
        Fixture().use { source ->
            val first = source.firstWorking()
            Fixture().use { destination ->
                destination.state.parentFile
                    .let(::checkNotNull)
                    .mkdirs()
                source.state.copyTo(destination.state, overwrite = true)
                source.retained.copyRecursively(destination.retained, overwrite = true)
                destination.authority.parentFile
                    .let(::checkNotNull)
                    .mkdirs()
                val stale = Uuid.random().toString()
                destination.accepted.writeText(stale)
                val destinationBefore = destination.database.readBytes()
                assertFalse(destination.store().canStageNativeDelivery())
                val parent = checkNotNull(destination.state.parentFile)
                val permissions = Files.getPosixFilePermissions(parent.toPath())
                Files.setPosixFilePermissions(parent.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))
                try {
                    assertFailsWith<IOException> { destination.store().prepare() }
                } finally {
                    Files.setPosixFilePermissions(parent.toPath(), permissions)
                }
                assertEquals(first.id, destination.store().current()?.id)
                assertFalse(destination.store().canStageNativeDelivery())
                assertEquals(stale, destination.accepted.readText())
                assertEquals(destinationBefore.toList(), destination.database.readBytes().toList())
            }
        }

    @Test fun pendingLocalTransactionsFinishBeforeLateNativeDelivery() {
        listOf("preparing-committed", "detaching-committed").forEach { boundary ->
            Fixture().use { fixture ->
                val first = fixture.firstWorking()
                assertFailsWith<IOException> {
                    fixture.store { if (it == boundary) throw IOException("interrupted pending retention") }.prepare()
                }
                val second = checkNotNull(fixture.store().current())
                assertNotEquals(first.id, second.id)
                assertFalse(fixture.store().canStageNativeDelivery())
                assertEquals("accepted working edits", fixture.database.readText())
                val restarted = fixture.store()
                assertTrue(restarted.prepareNativeDelivery(), "Late native routing must finish $boundary")
                assertEquals(RetainedJournalWorkspace.Phase.WORKING, restarted.current()?.phase)
                assertFalse(fixture.database.exists())
                val id = restarted.beginIncomingDelivery(Uuid.random().toString())
                val bytes = "late provider source".encodeToByteArray()
                restarted.captureIncomingFile(id, "database/logdate", bytes.size.toLong(), bytes.inputStream())
                restarted.finishIncomingDelivery()
                val retained = restarted.retainedSources()
                assertEquals("retained original", retained.getValue(first.id).getValue("database/logdate").readText())
                assertEquals("accepted working edits", retained.getValue(second.id).getValue("database/logdate").readText())
                assertEquals(
                    bytes.toList(),
                    restarted
                        .incomingPayloads()
                        .getValue(id)
                        .single()
                        .file
                        .readBytes()
                        .toList(),
                )
                assertFalse(fixture.database.exists(), "Late payload must remain staged")
            }
        }
    }

    @Test fun zeroFileCompletionFinishesLocalPendingTransactionWithoutInstallationReset() {
        listOf("preparing-committed", "detaching-committed").forEach { boundary ->
            Fixture().use { fixture ->
                fixture.firstWorking()
                assertFailsWith<IOException> {
                    fixture.store { if (it == boundary) throw IOException("interrupted pending retention") }.prepare()
                }
                val restarted = fixture.store()
                assertTrue(restarted.prepareNativeDelivery())
                restarted.finishIncomingDelivery()
                assertEquals(RetainedJournalWorkspace.Phase.WORKING, restarted.current()?.phase)
                assertTrue(restarted.incomingPayloads().isEmpty())
                assertTrue(restarted.canStageNativeDelivery())
                assertEquals(2, restarted.retainedSources().size)
            }
        }
    }

    @Test fun portablePendingTransactionsNeverAuthorizeCanonicalWritesOrDetachment() {
        listOf("preparing-committed", "detaching-committed").forEach { boundary ->
            Fixture().use { source ->
                assertFailsWith<IOException> {
                    source.store { if (it == boundary) throw IOException("interrupted pending retention") }.prepare()
                }
                Fixture().use { destination ->
                    destination.state.parentFile
                        .let(::checkNotNull)
                        .mkdirs()
                    source.state.copyTo(destination.state, overwrite = true)
                    if (source.retained.exists()) source.retained.copyRecursively(destination.retained, overwrite = true)
                    destination.authority.parentFile
                        .let(::checkNotNull)
                        .mkdirs()
                    destination.accepted.writeText(checkNotNull(source.store().current()).id)
                    val before = destination.database.readBytes()
                    assertFailsWith<IllegalStateException> { destination.store().prepareNativeDelivery() }
                    assertEquals(before.toList(), destination.database.readBytes().toList())
                    assertTrue(checkNotNull(destination.store().current()).incomingCohorts.isEmpty())
                }
            }
        }
    }

    @Test fun firstGenerationOrphanedLocalIntentResumesBeforeStartupOpensTheDatabase() =
        Fixture().use { fixture ->
            interruptBeforeFirstState(fixture)
            val abandonedId = fixture.authority.readText()
            val restarted = fixture.store()
            val completed = checkNotNull(restarted.resume())
            assertNotEquals(abandonedId, completed.id)
            assertTrue(restarted.canStageNativeDelivery())
            assertFalse(fixture.database.exists())
            assertEquals(
                "retained original",
                restarted
                    .retainedSources()
                    .getValue(completed.id)
                    .getValue("database/logdate")
                    .readText(),
            )
        }

    @Test fun firstGenerationOrphanedLocalIntentCommitsRetentionBeforeNativeRouting() =
        Fixture().use { fixture ->
            interruptBeforeFirstState(fixture)
            val restarted = fixture.store()
            assertTrue(restarted.prepareNativeDelivery())
            val session = checkNotNull(restarted.current())
            fixture.database.writeText("accepted working edits")
            val id = restarted.beginIncomingDelivery(Uuid.random().toString())
            val bytes = "late provider database".encodeToByteArray()
            restarted.captureIncomingFile(id, "database/logdate", bytes.size.toLong(), bytes.inputStream())
            restarted.finishIncomingDelivery()
            assertEquals("accepted working edits", fixture.database.readText())
            assertEquals(
                "retained original",
                restarted
                    .retainedSources()
                    .getValue(session.id)
                    .getValue("database/logdate")
                    .readText(),
            )
            assertEquals(
                bytes.toList(),
                restarted
                    .incomingPayloads()
                    .getValue(id)
                    .single()
                    .file
                    .readBytes()
                    .toList(),
            )
        }

    @Test fun malformedLocalIntentCannotAuthorizeStartupOrNativeRouting() =
        Fixture().use { fixture ->
            checkNotNull(fixture.authority.parentFile).mkdirs()
            fixture.authority.writeText("not a local recovery identifier")
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            assertFailsWith<IllegalStateException> { fixture.store().prepareNativeDelivery() }
            assertEquals("retained original", fixture.database.readText())
            assertFalse(fixture.state.exists())
            assertFalse(fixture.retained.exists())
        }

    @Test fun orphanedLocalIntentWithoutDatabaseCannotAdmitAnEmptyWorkingJournal() =
        Fixture().use { fixture ->
            interruptBeforeFirstState(fixture)
            check(fixture.database.delete())
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            assertFailsWith<IllegalStateException> { fixture.store().prepareNativeDelivery() }
            assertFalse(fixture.state.exists())
            assertFalse(fixture.retained.exists())
        }

    @Test fun missingStateCannotDiscardAnExistingRetainedGeneration() =
        Fixture().use { fixture ->
            val first = fixture.firstWorking()
            val manifest = File(fixture.retained, "${first.id}/manifest.json").readBytes().toList()
            check(fixture.state.delete())
            assertFailsWith<IllegalStateException> { fixture.store().resume() }
            assertFailsWith<IllegalStateException> { fixture.store().prepareNativeDelivery() }
            assertEquals("accepted working edits", fixture.database.readText())
            assertEquals(manifest, File(fixture.retained, "${first.id}/manifest.json").readBytes().toList())
        }

    private fun interruptBeforeFirstState(fixture: Fixture) {
        assertFailsWith<IOException> {
            fixture.store { if (it == "authority-committed") throw IOException("interrupted before first state") }.prepare()
        }
        assertTrue(fixture.authority.isFile)
        assertEquals(null, fixture.store().current())
        assertEquals("retained original", fixture.database.readText())
    }

    @Test fun nativeRoutingWithoutWorkspaceRemainsRawAndPortableWorkingRemainsUnadmitted() =
        Fixture().use { source ->
            assertFalse(source.store().prepareNativeDelivery())
            source.firstWorking()
            Fixture().use { destination ->
                destination.state.parentFile
                    .let(::checkNotNull)
                    .mkdirs()
                source.state.copyTo(destination.state, overwrite = true)
                source.retained.copyRecursively(destination.retained, overwrite = true)
                assertFalse(destination.store().prepareNativeDelivery())
                assertEquals("retained original", destination.database.readText())
            }
        }
}
