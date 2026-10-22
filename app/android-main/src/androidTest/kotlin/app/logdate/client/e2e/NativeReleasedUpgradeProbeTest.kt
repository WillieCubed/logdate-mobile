package app.logdate.client.e2e

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.AtomicFile
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioRecordingService
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/** Compiles against the released and candidate clients; only synthetic managed fixtures run it. */
@RunWith(AndroidJUnit4::class)
class NativeReleasedUpgradeProbeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private val marker get() = File(context.noBackupFilesDir, "native-released-upgrade.json")

    @Test
    fun releasedJournalRetainsItsIdentityAndPendingWork() =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val selectedStage = args.getString("releasedUpgradeStage")
            assumeTrue(selectedStage != null)
            val stage = requireNotNull(selectedStage)
            require(stage in setOf("baseline", "baseline-restart", "upgrade", "restore", "upgrade-restart", "restore-restart"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            val version = requireNotNull(args.getString("releasedUpgradeVersion")).toLong()
            val schema = requireNotNull(args.getString("releasedUpgradeSchema")).toInt()
            assertEquals(version, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            ActivityScenario.launch(MainActivity::class.java).use {
                // Force ordinary encrypted startup before reading any acceptance metadata.
                koin.get<LogDateDatabase>().journalDao().getAll()
                withTimeout(30_000) { koin.get<DatabaseStartupMonitor>().state.first { it == DatabaseStartupState.Ready } }
                assertNull(koin.get<SessionStorage>().captureAuthenticationSnapshot()?.session)
                if (stage == "baseline") {
                    require(!marker.exists()) { "A fresh released-source fixture is required" }
                    seedDraft()
                    writeMarker(snapshot(schema))
                } else {
                    val expected = JSONObject(marker.readText())
                    val actual = snapshot(schema)
                    for (key in listOf("owner", "fingerprint", "databaseKeySha256", "records", "legacyDraft")) {
                        assertEquals(expected.get(key).toString(), actual.get(key).toString(), "Released $key changed")
                    }
                    val sameInstallation = stage.startsWith("upgrade") || stage.startsWith("baseline")
                    if (sameInstallation) {
                        assertEquals(expected.getString("device"), actual.getString("device"))
                    } else {
                        assertNotEquals(expected.getString("device"), actual.getString("device"))
                    }
                    val before = Json.decodeFromString<EntryDraft>(expected.getString("draft"))
                    val after = Json.decodeFromString<EntryDraft>(actual.getString("draft"))
                    if (sameInstallation) {
                        assertEquals(before, after)
                        assertTrue(File(requireNotNull(after.pendingMedia.single().filePath)).isFile)
                        assertContentEquals(unfinishedRecording(), File(requireNotNull(after.pendingMedia.single().filePath)).readBytes())
                    } else {
                        val pending = after.pendingMedia.single()
                        assertEquals(
                            before.pendingMedia.single().filePath,
                            UnavailableMediaReferences.nativeSource(requireNotNull(pending.filePath)),
                        )
                        assertEquals(before.copy(pendingMedia = listOf(pending)), after)
                        assertEquals(before.pendingMedia.single().copy(filePath = pending.filePath), pending)
                        assertFalse(File(requireNotNull(before.pendingMedia.single().filePath)).exists())
                    }
                    if (!stage.startsWith("baseline")) {
                        val role = if (stage.startsWith("upgrade")) "upgrade" else "restore"
                        val receipt = File(context.noBackupFilesDir, "native-released-$role-device.txt")
                        if (stage.endsWith("-restart")) {
                            assertEquals(receipt.readText(), actual.getString("device"), "Restart replaced the installation identity")
                        } else {
                            require(!receipt.exists()) { "An earlier candidate device receipt must not be replaced" }
                            writeDurably(receipt, actual.getString("device"))
                        }
                    }
                }
                val recording = koin.get<AudioRecordingManager>()
                assertFalse(recording.isRecording)
                assertFalse(recording.isStartingRecording)
                @Suppress("DEPRECATION")
                val running = context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                assertTrue(running.none { it.service.className == AudioRecordingService::class.java.name })
            }
        }

    private suspend fun seedDraft() {
        val drafts = koin.get<EntryDraftRepository>()
        assertTrue(drafts.getDrafts().first().none { it.id == id(60) })
        val at = Instant.parse("2020-02-03T04:05:06Z")
        val partial = File(context.cacheDir, "recordings/released-upgrade-partial.m4a")
        assertTrue(partial.parentFile?.mkdirs() == true || partial.parentFile?.isDirectory == true)
        assertTrue(partial.createNewFile())
        partial.writeBytes(unfinishedRecording())
        drafts.createDraft(
            id(60),
            listOf(JournalNote.Text(id(61), at, at, "Released unfinished editor draft")),
            listOf(
                PendingMediaRecord(
                    id(62),
                    PendingMediaType.AUDIO,
                    at,
                    filePath = partial.absolutePath,
                    timeZoneId = "America/Los_Angeles",
                    position = 0,
                    caption = "Released unfinished caption",
                    transcription = "Released unfinished transcript",
                ),
            ),
            selectedJournalIds = listOf(id(1), id(2)),
            visitContext = null,
            markId = "released-mark",
        )
    }

    private suspend fun snapshot(expectedSchema: Int): JSONObject {
        val key = requireNotNull(PassphraseBackupStore(context).read())
        val file = context.getDatabasePath("logdate")
        assertTrue(file.isFile)
        assertFalse(file.inputStream().use { it.readNBytes(16) }.contentEquals("SQLite format 3\u0000".encodeToByteArray()))
        val records =
            CipherDatabase.openDatabase(file.absolutePath, key.copyOf(), null, CipherDatabase.OPEN_READONLY, null, null).use { database ->
                database.rawQuery("PRAGMA user_version", emptyArray<String>()).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(expectedSchema, cursor.getInt(0))
                }
                JSONArray().apply {
                    for (query in listOf(
                        "SELECT id, title, description, created, lastUpdated, syncVersion, lastSynced, deletedAt, coverImageUri FROM journals ORDER BY id",
                        "SELECT uid, content, created, lastUpdated, syncVersion, lastSynced, deletedAt, time_zone_id, captured_on, captured_during FROM text_notes ORDER BY uid",
                        "SELECT uid, contentUri, created, lastUpdated, syncVersion, lastSynced, deletedAt, time_zone_id FROM image_notes ORDER BY uid",
                        "SELECT uid, contentUri, durationMs, created, lastUpdated, syncVersion, lastSynced, deletedAt, time_zone_id FROM audio_notes ORDER BY uid",
                        "SELECT uid, contentUri, durationMs, created, lastUpdated, syncVersion, lastSynced, deletedAt, time_zone_id FROM video_notes ORDER BY uid",
                        "SELECT id, uid FROM journal_notes ORDER BY id, uid",
                        "SELECT ownerId, serverOrigin, entityType, entityId, operation, retryCount, operationId FROM pending_uploads ORDER BY operationId",
                    )) {
                        database.rawQuery(query, emptyArray<String>()).use { cursor ->
                            while (cursor.moveToNext()) {
                                put(
                                    JSONArray().apply {
                                        put(query)
                                        for (column in 0 until cursor.columnCount) put(cursor.getString(column) ?: JSONObject.NULL)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        val identity = koin.get<IdentityKeyManager>()
        val owner = koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId()
        val backup = requireNotNull(AndroidIdentityKeyBackupStore(context).readAccountIdentity())
        assertEquals(owner, backup.accountId, "Portable identity belongs to a different journal")
        val fingerprint = IdentityKeyFingerprint.of(backup.key, koin.get<CryptoManager>())
        if (identity.hasIdentityKey()) {
            assertEquals(fingerprint, IdentityKeyFingerprint.of(identity.getIdentityKey(), koin.get<CryptoManager>()))
        }
        return JSONObject()
            .put("owner", koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
            .put(
                "device",
                koin
                    .get<DeviceIdProvider>()
                    .getDeviceId()
                    .value
                    .toString(),
            ).put("fingerprint", fingerprint)
            .put("databaseKeySha256", MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it) })
            .put("records", records)
            .put("legacyDraft", Json.encodeToString(portableLegacyDraft(requireNotNull(koin.get<JournalRepository>().getDraft(id(10))))))
            .put(
                "draft",
                Json.encodeToString(
                    koin
                        .get<EntryDraftRepository>()
                        .getDraft(id(60))
                        .first()
                        .getOrThrow(),
                ),
            )
    }

    private fun portableLegacyDraft(draft: EditorDraft): EditorDraft {
        val references = koin.get<StoredMediaReferences>()

        fun portable(value: String?) = value?.let(references::storedReference)
        return draft.copy(
            blocks =
                draft.blocks.map { block ->
                    when (block) {
                        is SerializableImageBlock -> block.copy(uri = portable(block.uri))
                        is SerializableAudioBlock -> block.copy(uri = portable(block.uri))
                        is SerializableVideoBlock -> block.copy(uri = portable(block.uri), thumbnailUri = portable(block.thumbnailUri))
                        is SerializableCameraBlock -> block.copy(uri = portable(block.uri))
                        else -> block
                    }
                },
        )
    }

    private fun writeMarker(value: JSONObject) {
        writeDurably(marker, value.toString())
    }

    private fun writeDurably(
        file: File,
        value: String,
    ) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(value.encodeToByteArray())
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun id(number: Int) = Uuid.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")

    private fun unfinishedRecording() = "unfinished recording excluded from native backup".encodeToByteArray()
}
