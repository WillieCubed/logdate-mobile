package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativePrivacyRepairDeliveryTest {
    @Test
    fun `only real completion can repair the same journal without changing its native identity`() =
        fixture { f ->
            val before = f.markers()
            f.deliverComplete()
            assertNull(f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.delivery.finishIfRequired())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertEquals("destination", f.store.data.value[f.device])
            assertEquals(before, f.markers())
            assertEquals("original database", f.database.readText())
            assertTrue(f.inspected)
            assertTrue(f.custody.hasInputs(), "Unproven auxiliary inputs must not disappear")
        }

    @Test
    fun `matching protected revision repairs without discarding other complete revisions`() =
        fixture { f ->
            val identity = f.markers()
            f.deliverComplete()
            f.deliver("database/logdate", "another complete revision")
            assertTrue(f.delivery.finishIfRequired())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertEquals(identity, f.markers())
            assertEquals(2, f.custody.sealedPayloads().count { it.path == "database/logdate" })
        }

    @Test
    fun `only a fully covered acknowledged repair releases backup refusal and preserves source bytes`() =
        fixture { f ->
            f.accountingEnabled = true
            f.deliver("files/logdate.preferences_pb", "known preferences")
            f.deliver("database/logdate", f.database.readText())
            assertTrue(f.custody.hasInputs())
            assertTrue(f.delivery.finishIfRequired())
            assertFalse(f.custody.hasInputs(), "Represented bytes must not block every subsequent native backup")
            val directory = File(f.noBackup, "native-journal-replay-custody").listFiles()!!.single()
            val hash = retainedJournalChecksum(File(directory, "manifest.json"))
            val retained = f.custody.verifiedCohort(directory.name, hash)
            assertEquals(2, retained.size)
            assertEquals("original database", retained.single { it.path == "database/logdate" }.file.readText())
        }

    @Test
    fun `an unproven extra retains source custody even after policy acknowledgement`() =
        fixture { f ->
            f.accountingEnabled = true
            f.deliverComplete()
            assertTrue(f.delivery.finishIfRequired())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertTrue(f.custody.hasInputs())
            assertTrue(f.noBackup.walkTopDown().none { it.name == "privacy-accounted.json" })
        }

    @Test
    fun `sealed historical custody never becomes a completed framework policy proof`() =
        fixture { f ->
            val cohort = Uuid.random().toString()
            val before = f.markers()
            f.custody.captureInCohort(cohort, "files/logdate.preferences_pb", 17, "known preferences".byteInputStream())
            f.database.inputStream().use { f.custody.captureInCohort(cohort, "database/logdate", f.database.length(), it) }
            f.custody.sealedPayloads()
            assertTrue(f.newDelivery().finishIfRequired(), "Must not reset the original account delivery")
            assertNull(f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertEquals(before, f.markers())
        }

    @Test
    fun `root directory callback is handled without mapping outside a child backup domain`() =
        fixture { f ->
            val before = f.markers()
            val handled =
                f.delivery.captureIfRequired(BackupAgent.TYPE_DIRECTORY, { error("Directory roots have no child mapping") }) { _, _ ->
                    error("No directory payload")
                }
            assertTrue(handled)
            assertTrue(f.delivery.finishIfRequired())
            assertEquals(before, f.markers())
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `known or uncompleted policy state cannot start this repair path`() =
        fixture(privacyReceived = true) { f ->
            f.store.data.value =
                f.store.data.value
                    .toMutablePreferences()
                    .apply { this[f.security] = "NONE" }
            val known =
                f.newDelivery().captureIfRequired(BackupAgent.TYPE_FILE, { error("Known policy must keep ordinary routing") }) { _, _ ->
                    error("Not owned")
                }
            assertFalse(known)
            f.store.data.value =
                f.store.data.value
                    .toMutablePreferences()
                    .apply { remove(f.security) }
            f.detector.beginNativeRestoreSession()
            val unfinished =
                f.newDelivery().captureIfRequired(BackupAgent.TYPE_FILE, { error("An unfinished restore owns itself") }) { _, _ ->
                    error("Not owned")
                }
            assertFalse(unfinished)
        }

    @Test
    fun `stale unreceived typed policy remains blocked until the matching original delivery completes`() =
        fixture { f ->
            f.store.data.value =
                f.store.data.value
                    .toMutablePreferences()
                    .apply { this[f.security] = "NONE" }
            f.deliverComplete()
            assertEquals("NONE", f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
            assertTrue(f.delivery.finishIfRequired())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
            assertFalse(f.detector.hasMissingNativePrivacyPayload())
        }

    @Test
    fun `a received file with missing typed policy still requires actual matching completion`() =
        fixture(privacyReceived = true) { f ->
            f.deliverComplete()
            assertNull(f.store.data.value[f.security])
            assertTrue(f.delivery.finishIfRequired())
            assertEquals("BIOMETRIC", f.store.data.value[f.security])
        }

    @Test
    fun `different source journal stays in custody and cannot repair privacy`() =
        fixture { f ->
            f.deliver("database/logdate", "other journal")
            f.deliver("files/logdate.preferences_pb", "known preferences")
            assertFailsWith<IllegalStateException> { f.delivery.finishIfRequired() }
            assertEquals("original database", f.database.readText())
            assertNull(f.store.data.value[f.security])
            assertTrue(f.custody.hasInputs())
        }

    @Test
    fun `changed canonical journal between callbacks preserves the independent write`() =
        fixture { f ->
            f.deliverComplete()
            f.database.writeText("independent changed journal")
            assertFailsWith<IllegalStateException> { f.delivery.finishIfRequired() }
            assertEquals("independent changed journal", f.database.readText())
            assertNull(f.store.data.value[f.security])
        }

    @Test
    fun `partial or changed captured bytes never authorize a policy`() =
        fixture { f ->
            assertFailsWith<IOException> {
                f.delivery.captureIfRequired(BackupAgent.TYPE_FILE, { "database/logdate" }) { id, path ->
                    f.custody.captureInCohort(id, path, 100, "partial".byteInputStream())
                }
            }
            assertFailsWith<IllegalStateException> { f.delivery.finishIfRequired() }
            assertTrue(f.custody.hasInputs())
            assertNull(f.store.data.value[f.security])
        }

    @Test
    fun `source callback tampering is detected by the real immutable cohort manifest`() =
        fixture { f ->
            f.deliverComplete()
            val captured = File(f.noBackup, "native-journal-replay-custody").walkTopDown().first { it.name == "payload" }
            captured.writeText("different captured bytes")
            assertFailsWith<IllegalStateException> { f.delivery.finishIfRequired() }
            assertNull(f.store.data.value[f.security])
            assertTrue(f.detector.hasMissingNativePrivacyPayload())
        }

    private fun fixture(
        privacyReceived: Boolean = false,
        test: (Fixture) -> Unit,
    ) {
        val root = Files.createTempDirectory("privacy-completed-delivery").toFile()
        try {
            test(Fixture(root, privacyReceived))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
        privacyReceived: Boolean,
    ) {
        val security = stringPreferencesKey("security_level")
        val device = stringPreferencesKey("device.id")
        val noBackup = File(root, "no-backup").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        val database = File(root, "logdate").apply { writeText("original database") }
        val context =
            mockk<Context>().also { context ->
                every { context.noBackupFilesDir } returns noBackup
                every { context.filesDir } returns files
                every { context.applicationInfo } returns ApplicationInfo().apply { dataDir = root.absolutePath }
                every { context.getDatabasePath(any()) } answers { File(root, firstArg<String>()) }
            }
        val detector = PostRestoreDetector(context)
        val custody = NativeJournalReplayCustody(context)
        val store = Store(mutablePreferencesOf(stringPreferencesKey("identity.canonical_owner_id") to "owner", device to "destination"))
        val incoming =
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to "owner",
                device to "source",
                security to "BIOMETRIC",
            )
        var inspected = false
        var accountingEnabled = false
        val delivery: NativePrivacyRepairDelivery

        init {
            detector.beginNativeRestoreSession(retainPreferences = true)
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            if (privacyReceived) detector.markNativePrivacyPayloadReceived()
            detector.reserveAccountRecoveryForCompletion()
            detector.recordRestoreCompleted()
            assertNotNull(detector.capturePrivacyRepairIdentity())
            delivery = newDelivery()
        }

        fun newDelivery() =
            NativePrivacyRepairDelivery(context, store, { selected ->
                inspected = true
                assertEquals("original database", selected.getValue("database/logdate").readText())
            }, { file ->
                check(file.readText() == "known preferences")
                incoming
            }, { mapOf("database/logdate" to database) }, {
                mapOf(
                    "database/logdate" to
                        MessageDigest.getInstance("SHA-256").digest(database.readBytes()).joinToString("") { "%02x".format(it) },
                )
            }, accountSource = { acknowledgement ->
                if (accountingEnabled) accountNativePrivacyRepairSource(context, store, acknowledgement)
            })

        fun deliver(
            path: String,
            bytes: String,
        ) {
            assertTrue(
                delivery.captureIfRequired(BackupAgent.TYPE_FILE, { path }) { id, destination ->
                    custody.captureInCohort(id, destination, bytes.encodeToByteArray().size.toLong(), bytes.byteInputStream())
                },
            )
        }

        fun deliverComplete() {
            deliver("files/logdate.preferences_pb", "known preferences")
            deliver("database/logdate", database.readText())
            deliver("files/datastore/unproven-extra", "retained extra input")
        }

        fun markers() =
            noBackup
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.name != ".logdate_native_restore_privacy_received" &&
                        !it.name.startsWith(
                            "native-privacy-repair",
                        )
                }.associate { it.name to it.readBytes().toList() }
    }

    private class Store(
        initial: Preferences,
    ) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
