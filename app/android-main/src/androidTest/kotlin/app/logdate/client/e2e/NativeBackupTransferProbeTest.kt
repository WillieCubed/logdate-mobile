package app.logdate.client.e2e

import android.Manifest
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingSessionOptions
import app.logdate.client.media.storage.UnavailableMediaReferences
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.AutomaticMediaRecovery
import app.logdate.client.sync.AutomaticMediaRecoveryResult
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Native-transport probe; only runs when the host explicitly selects its disposable fixture. */
@RunWith(AndroidJUnit4::class)
class NativeBackupTransferProbeTest {
    @Test
    fun nativeJournalTransfer(): Unit =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val mode = InstrumentationRegistry.getArguments().getString("nativeBackupMode")
            assumeTrue(
                "requires the native backup acceptance harness",
                mode in
                    setOf(
                        "seed",
                        "verify",
                        "verify-in-place",
                        "quota",
                        "quota-cleanup",
                        "missing-keys",
                        "credentials-only",
                        "source-account-session",
                        "source-account-session-cleanup",
                        "source-backup-integrity",
                        "data-only",
                        "partial-media",
                        "compact-transfer",
                        "recording-policy",
                        "post-backup-mutation",
                        "native-restore-status",
                    ),
            )
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
            require(instrumentation.targetContext.packageName.endsWith(".debug")) { "Isolated debug application required" }
            val context = instrumentation.targetContext
            if (mode == "native-restore-status") {
                assertEquals(
                    PostRestoreType.CONTENT_RESTORED,
                    PostRestoreDetector(context).detect(true),
                    "Android must deliver the journal through the native restore callback before app verification",
                )
                return@runBlocking
            }
            if (mode == "recording-policy") {
                verifyPhoneRecordingBackupPolicy(instrumentation, context)
                return@runBlocking
            }
            ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
            val koin = GlobalContext.get()
            koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
            if (mode == "source-account-session") {
                val sessions = koin.get<SessionStorage>()
                assertNull(sessions.getSession(), "Data-only acceptance requires a signed-out synthetic source")
                val owner = koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId()
                sessions.saveExplicitSession(UserSession("native-acceptance-access", "native-acceptance-refresh", owner))
                assertEquals(owner, sessions.getSession()?.accountId)
                return@runBlocking
            }
            if (mode == "source-account-session-cleanup") {
                val sessions = koin.get<SessionStorage>()
                sessions.clearSession()
                assertNull(sessions.getSession(), "Synthetic data-only source session was not cleared")
                return@runBlocking
            }
            if (mode == "source-backup-integrity") {
                val arguments = InstrumentationRegistry.getArguments()
                val identity = koin.get<IdentityKeyManager>()
                val crypto = koin.get<CryptoManager>()
                assertEquals(
                    arguments.getString("sourceFingerprint"),
                    IdentityKeyFingerprint.of(identity.getIdentityKey(), crypto),
                    "Native backup must not replace the source identity key",
                )
                val mediaKey =
                    requireNotNull(koin.get<SecureStorage>().getBytes("media_payload_key_v1")) {
                        "Native backup removed the source media encryption key"
                    }
                assertContentEquals(
                    "cloud media payload".encodeToByteArray(),
                    crypto.aesGcmDecrypt(
                        mediaKey,
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaNonce"))),
                        "native-transfer".encodeToByteArray(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaCiphertext"))),
                    ),
                    "Native backup changed the key for original Cloud media",
                )
                val sessions = koin.get<SessionStorage>()
                if (arguments.getString("sourceHasSession") == "true") {
                    assertTrue(
                        sessions.hasValidSession(),
                        "Persisted synthetic source session did not reload after native backup",
                    )
                    val session = requireNotNull(sessions.getSession())
                    assertEquals("native-acceptance-access", session.accessToken)
                    assertEquals("native-acceptance-refresh", session.refreshToken)
                    assertEquals(arguments.getString("sourceOwner"), session.accountId)
                } else {
                    assertFalse(
                        sessions.hasValidSession(),
                        "Native backup restored a source account session that should be absent",
                    )
                    assertNull(sessions.getSession(), "Native backup unexpectedly created a source account session")
                }
                return@runBlocking
            }
            if (mode == "missing-keys") {
                verifyMissingRecoveryKeys(context, koin)
                return@runBlocking
            }
            if (mode == "credentials-only") {
                val arguments = InstrumentationRegistry.getArguments()
                val identity = koin.get<IdentityKeyManager>()
                identity.restoreFromBackupIfAvailable()
                assertTrue(PassphraseBackupStore(context).hasBackup(), "Credentials-only restore lost the journal recovery key")
                assertTrue(AndroidIdentityKeyBackupStore(context).hasBackup(), "Credentials-only restore lost portable identity material")
                assertEquals(arguments.getString("sourceOwner"), koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
                val destinationDeviceId =
                    koin
                        .get<DeviceIdProvider>()
                        .getDeviceId()
                        .value
                        .toString()
                assertNotEquals(
                    arguments.getString("sourceDevice"),
                    destinationDeviceId,
                    "A replacement device must create its own device id",
                )
                assertEquals(
                    arguments.getString("sourceFingerprint"),
                    IdentityKeyFingerprint.of(identity.getIdentityKey(), koin.get<CryptoManager>()),
                    "Credentials-only restore must retain the journal identity",
                )
                assertContentEquals(
                    "cloud media payload".encodeToByteArray(),
                    koin.get<CryptoManager>().aesGcmDecrypt(
                        koin.get<MediaPayloadKeyProvider>().getOrCreateKey(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaNonce"))),
                        "native-transfer".encodeToByteArray(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaCiphertext"))),
                    ),
                    "Credentials-only restore must retain the key that decrypts the source media",
                )
                if (InstrumentationRegistry.getArguments().getString("nativeIdentityMode") == "account") {
                    assertNull(identity.getStoredRecoveryPhrase())
                }
                val database = koin.get<LogDateDatabase>()
                assertTrue(database.journalDao().getAll().isEmpty(), "Credentials-only restore must not recreate journals")
                assertTrue(database.textNoteDao().getAll().isEmpty(), "Credentials-only restore must not recreate text notes")
                assertTrue(database.imageNoteDao().getAll().isEmpty(), "Credentials-only restore must not recreate image notes")
                assertTrue(database.videoNoteDao().getAll().isEmpty(), "Credentials-only restore must not recreate video notes")
                assertTrue(database.audioNoteDao().getAll().isEmpty(), "Credentials-only restore must not recreate audio notes")
                assertTrue(
                    koin.get<JournalRepository>().getAllDrafts().none { draft ->
                        draft.blocks
                            .filterIsInstance<SerializableTextBlock>()
                            .any { it.content == "Unfinished native-transfer draft" }
                    },
                    "Credentials-only restore must not recreate the source's unfinished draft",
                )
                return@runBlocking
            }
            val fixture = NativeJournalFixture(koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
            if (mode == "post-backup-mutation") {
                val fixtureFile =
                    assertNotNull(
                        File(context.noBackupFilesDir, "native-backup-fixture.json").takeIf(File::isFile),
                        "Only the synthetic native backup fixture may be mutated",
                    )
                val fixtureMetadata = JSONObject(fixtureFile.readText())
                assertTrue(fixtureMetadata.optBoolean("recordingTransferFixture"))
                assertTrue(fixtureMetadata.optString("owner").isNotBlank())
                assertTrue(fixtureMetadata.optString("device").isNotBlank())
                val journals = koin.get<JournalRepository>()
                val journalId = Uuid.parse("00000000-0000-0000-0000-000000000001")
                val journal = requireNotNull(journals.getJournalById(journalId)) { "Synthetic journal fixture is missing" }
                assertEquals("Native transfer journal", journal.title, "Only the expected synthetic journal may be mutated")
                journals.update(journal.copy(title = "Mutated after native D2D backup"))
                assertEquals("Mutated after native D2D backup", journals.getJournalById(journalId)?.title)
                val recording = File(context.filesDir, "audio_notes/transfer-recording.m4a")
                assertContentEquals("committed recording".encodeToByteArray(), recording.readBytes())
                val replacement = "Changed after native D2D backup".encodeToByteArray()
                recording.writeBytes(replacement)
                assertContentEquals(replacement, recording.readBytes())
                return@runBlocking
            }
            if (mode == "data-only") {
                assertNull(koin.get<SessionStorage>().getSession(), "Native app-data restore must not invent an account session")
            }
            if (mode == "compact-transfer") {
                require(File(context.noBackupFilesDir, "native-backup-fixture.json").isFile) {
                    "Synthetic source fixture required before removing the quota-only row"
                }
                fixture.removeQuotaOnlyNoteAndCompact(context, koin.get())
                return@runBlocking
            }
            if (mode == "quota") {
                fixture.exceedCloudQuota(context)
                return@runBlocking
            }
            if (mode == "quota-cleanup") {
                NativeJournalFixture.cleanupQuotaPayload(context)
                return@runBlocking
            }
            val identity = koin.get<IdentityKeyManager>()
            val identityMode = InstrumentationRegistry.getArguments().getString("nativeIdentityMode")
            val legacyAccount = identityMode == "legacy-account"
            val accountIdentity = identityMode == "account" || legacyAccount
            val crypto = koin.get<CryptoManager>()
            var sourceMediaKey: ByteArray? = null
            if (mode == "seed") {
                if (accountIdentity) {
                    val owner = koin.get<CanonicalOwnerProvider>()
                    val accountId = owner.getCanonicalOwnerId()
                    assertTrue(owner.adoptRemoteOwnerIfUninitialized(accountId))
                    if (legacyAccount) {
                        // Model an enrolled build predating portable account and media key backups.
                        val secure = koin.get<SecureStorage>()
                        secure.putBytes("identity_key_v1", crypto.generateRandomBytes(32))
                        secure.putString("identity_account_id_v1", accountId)
                        sourceMediaKey = crypto.generateRandomBytes(32)
                        secure.putBytes("media_payload_key_v1", requireNotNull(sourceMediaKey))
                    } else {
                        identity.installAccountKey(accountId, crypto.generateRandomBytes(32))
                    }
                    assertNull(identity.getStoredRecoveryPhrase())
                } else {
                    identity.ensureIdentityKey()
                }
                val mediaKey = sourceMediaKey ?: koin.get<MediaPayloadKeyProvider>().getOrCreateKey()
                val mediaNonce = crypto.generateRandomBytes(12)
                val mediaCiphertext =
                    crypto.aesGcmEncrypt(
                        mediaKey,
                        mediaNonce,
                        "native-transfer".encodeToByteArray(),
                        "cloud media payload".encodeToByteArray(),
                    )
                fixture.seed(context)
                koin.get<UserStateRepository>().setIsOnboardingComplete(true)
                val metadata =
                    JSONObject()
                        .put("owner", koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
                        .put(
                            "device",
                            koin
                                .get<DeviceIdProvider>()
                                .getDeviceId()
                                .value
                                .toString(),
                        ).put("fingerprint", IdentityKeyFingerprint.of(identity.getIdentityKey(), crypto))
                        .put("mediaNonce", Base64.encode(mediaNonce))
                        .put("mediaCiphertext", Base64.encode(mediaCiphertext))
                        .put("recordingTransferFixture", true)
                File(instrumentation.targetContext.noBackupFilesDir, "native-backup-fixture.json").writeText(metadata.toString())
                if (legacyAccount) {
                    assertFalse(
                        AndroidIdentityKeyBackupStore(instrumentation.targetContext).hasBackup(),
                        "Legacy upgrade source must have no portable identity or media backup before Android backs it up",
                    )
                }
            } else {
                val arguments = InstrumentationRegistry.getArguments()
                identity.restoreFromBackupIfAvailable()
                if (accountIdentity) assertNull(identity.getStoredRecoveryPhrase())
                assertEquals(arguments.getString("sourceOwner"), koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
                val restoredDevice =
                    koin
                        .get<DeviceIdProvider>()
                        .getDeviceId()
                        .value
                        .toString()
                if (mode == "verify-in-place") {
                    assertEquals(arguments.getString("sourceDevice"), restoredDevice, "In-place restore must retain the installation ID")
                } else {
                    assertNotEquals(
                        arguments.getString("sourceDevice"),
                        restoredDevice,
                        "A fresh target must have a distinct installation ID",
                    )
                }
                assertEquals(
                    arguments.getString("sourceFingerprint"),
                    IdentityKeyFingerprint.of(identity.getIdentityKey(), koin.get<CryptoManager>()),
                )
                assertContentEquals(
                    "cloud media payload".encodeToByteArray(),
                    crypto.aesGcmDecrypt(
                        koin.get<MediaPayloadKeyProvider>().getOrCreateKey(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaNonce"))),
                        "native-transfer".encodeToByteArray(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaCiphertext"))),
                    ),
                    "Transferred encryption keys cannot decrypt the original Cloud media payload",
                )
                if (arguments.getString("sourceRecordingTransferFixture") == "true") {
                    verifyRecordingTransferFixture(context, inPlace = mode == "verify-in-place")
                }
                fixture.verifyUnfinishedCameraCapture(context, inPlace = mode == "verify-in-place")
                if (mode == "partial-media") {
                    fixture.verifyPartialTransfer(requireNotNull(arguments.getString("expectedMissingMediaPath")))
                    return@runBlocking
                }
                fixture.verifyAndEdit()
            }
        }
}

private suspend fun verifyPhoneRecordingBackupPolicy(
    instrumentation: android.app.Instrumentation,
    context: android.content.Context,
) {
    ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
    val permissions =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    permissions.forEach { permission ->
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }
    val final = File(context.filesDir, "audio_notes/native-recording-policy-${Uuid.random()}.m4a")
    val staged = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
    val controller =
        AndroidRecordingServiceController(
            context,
            RecordingSessionOptions(crashSafe = true, excludeIncompleteFromBackup = true),
        )
    try {
        assertTrue(controller.start(final.absolutePath, inputDeviceId = null), "Recording service refused to start")
        val state =
            withTimeout(10_000) {
                controller.serviceState.filterNotNull().first { it.isRecording || it.failureMessage != null }
            }
        assertTrue(state.isRecording, state.failureMessage ?: "Recording service did not start")
        withTimeout(5_000) {
            while (!staged.isFile) delay(25)
        }
        assertFalse(final.exists(), "The in-flight phone recording was visible in the transferable media directory")
        delay(2_500)
        assertEquals(final.absolutePath, controller.stopRecordingNow())
        assertTrue(final.isFile && final.length() > 0L, "The finalized phone recording was not published")
        assertFalse(staged.exists(), "The finalized phone recording left its staging file behind")
    } finally {
        try {
            controller.shutdown()
        } finally {
            final.delete()
            staged.delete()
        }
    }
}

private suspend fun verifyMissingRecoveryKeys(
    context: android.content.Context,
    koin: org.koin.core.Koin,
) {
    val databaseFile = context.getDatabasePath("logdate")
    assertTrue(databaseFile.isFile && databaseFile.length() > 0, "Native restore did not retain the encrypted journal database")
    val originalDatabaseDigest = MessageDigest.getInstance("SHA-256").digest(databaseFile.readBytes())
    val passphraseBackup = PassphraseBackupStore(context)
    val identityBackup = AndroidIdentityKeyBackupStore(context)
    assertFalse(passphraseBackup.hasBackup(), "The synthetic missing-key transport still contains the database key")
    assertFalse(identityBackup.hasBackup(), "The synthetic missing-key transport still contains identity recovery keys")

    val startupMonitor = koin.get<DatabaseStartupMonitor>()
    assertFailsWith<Exception>("Database startup must refuse a replacement key for retained encrypted data") {
        koin.get<LogDateDatabase>()
    }
    val recovery = startupMonitor.state.value as? DatabaseStartupState.RecoveryRequired
    assertNotNull(recovery, "Missing recovery material must leave database startup in a recoverable state")
    assertTrue(
        recovery.detail.contains("original encryption key"),
        "Recovery should identify the missing journal key without exposing key material",
    )
    ActivityScenario.launch(MainActivity::class.java).use { activity ->
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            device.wait(Until.hasObject(By.text("Finish restoring your journal")), 15_000),
            "Missing-key recovery must be reachable before the database-backed application graph",
        )
        assertTrue(device.hasObject(By.textContains("original device")), "Recovery must explain how to obtain the original key")
        activity.recreate()
        assertTrue(device.wait(Until.hasObject(By.text("Finish restoring your journal")), 15_000))
    }
    assertNull(koin.get<SecureStorage>().getString("db_encryption_key"), "Startup persisted a replacement database key")
    assertTrue(databaseFile.isFile, "Recovery handling deleted the retained encrypted journal")
    assertContentEquals(
        originalDatabaseDigest,
        MessageDigest.getInstance("SHA-256").digest(databaseFile.readBytes()),
        "Recovery handling changed the retained encrypted journal",
    )
}

private fun verifyRecordingTransferFixture(
    context: android.content.Context,
    inPlace: Boolean,
) {
    val final = File(context.filesDir, "audio_notes/transfer-recording.m4a")
    val inFlight = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
    val remux = CrashSafeRecording.remuxStagingFile(inFlight, final)
    assertTrue(final.isFile && final.readBytes().contentEquals("committed recording".encodeToByteArray()))
    if (inPlace) {
        assertContentEquals(
            "unfinished raw recording".encodeToByteArray(),
            inFlight.readBytes(),
            "In-place restore must retain excluded source data",
        )
    } else {
        assertFalse(inFlight.exists(), "An interrupted raw recording must not transfer as committed audio")
        assertFalse(remux.exists(), "An unfinished remux temporary must not transfer")
    }
}

private class NativeJournalFixture(
    private val journals: JournalRepository,
    private val notes: JournalNotesRepository,
    private val links: JournalContentRepository,
    private val media: MediaManager,
    private val syncMetadata: SyncMetadataService,
) {
    suspend fun removeQuotaOnlyNoteAndCompact(
        context: android.content.Context,
        database: LogDateDatabase,
    ) {
        notes.removeById(id(999))
        syncMetadata
            .getPendingUploads(EntityType.NOTE)
            .filter { it.entityId == id(999).toString() }
            .forEach { pending ->
                syncMetadata.markAsSynced(pending.entityId, EntityType.NOTE, Clock.System.now(), version = 0)
            }
        check(!syncMetadata.hasPending(EntityType.NOTE, id(999).toString())) {
            "Quota-only note left a pending payload in the synthetic source"
        }
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
        db.execSQL("VACUUM")
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
        assertNull(notes.getNoteById(id(999)), "Quota-only fixture row survived cleanup")
        val fileSize = context.getDatabasePath("logdate").length()
        val pageCount =
            db.query("PRAGMA page_count").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        val freePages =
            db.query("PRAGMA freelist_count").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        check(fileSize < MAX_NATIVE_TRANSFER_FIXTURE_DATABASE_BYTES) {
            "Synthetic transfer fixture exceeds its size guard after removing the quota-only row " +
                "($fileSize bytes, database=${database.openHelper.databaseName}, pages=$pageCount, freePages=$freePages)"
        }
    }

    fun exceedCloudQuota(context: android.content.Context) {
        val payload = context.filesDir.resolve("db_recovery/native-backup-quota-acceptance.bin")
        val directory = checkNotNull(payload.parentFile)
        if (!directory.isDirectory) check(directory.mkdirs())
        check(payload.createNewFile()) { "Quota acceptance payload already exists" }
        val random = java.util.Random(0x4c6f6744617465L)
        val buffer = ByteArray(64 * 1024)
        payload.outputStream().buffered().use { output ->
            repeat(28 * 1024 * 1024 / buffer.size) {
                random.nextBytes(buffer)
                output.write(buffer)
            }
        }
    }

    companion object {
        fun cleanupQuotaPayload(context: android.content.Context) {
            val payload = context.filesDir.resolve("db_recovery/native-backup-quota-acceptance.bin")
            if (payload.exists()) check(payload.delete()) { "Could not remove quota acceptance payload" }
        }
    }

    suspend fun seed(context: android.content.Context) {
        assertNull(journals.getJournalById(id(1)), "fixture already exists; use fresh emulator data")
        journals.create(Journal(id = id(1), title = "Native transfer journal"))
        journals.create(Journal(id = id(2), title = "Second transferred journal"))
        val now = Clock.System.now()
        notes.create(JournalNote.Text(id(3), now, now, "Text retained through native backup"), id(1))
        links.addContentToJournal(id(3), id(2))
        val unfinishedCapture = File(context.filesDir, "camera-captures/logdate-camera-unfinished.mp4")
        check(unfinishedCapture.parentFile?.mkdirs() == true || unfinishedCapture.parentFile?.isDirectory == true)
        unfinishedCapture.writeBytes("unfinished camera output".encodeToByteArray())
        val image = save("proof.png", "image/png")
        val audio = save("proof.wav", "audio/wav")
        val video = save("proof.mp4", "video/mp4")
        seedRecordingTransferFixture(context)
        notes.create(JournalNote.Image(id(4), now, now, image, caption = "Original photo"), id(1))
        notes.create(JournalNote.Audio(uid = id(5), creationTimestamp = now, lastUpdated = now, mediaRef = audio, durationMs = 500L), id(2))
        notes.create(JournalNote.Video(id(6), now, now, video, caption = "Original video"), id(1))
        notes.create(JournalNote.Text(id(7), now, now, "Deleted before transfer"), id(1))
        notes.removeById(id(7))
        journals.saveDraft(
            EditorDraft(
                id = id(10),
                blocks =
                    listOf(
                        SerializableTextBlock(id(11), now, content = "Unfinished native-transfer draft"),
                        SerializableImageBlock(id(12), now, uri = image),
                        SerializableAudioBlock(id(13), now, uri = audio),
                        SerializableVideoBlock(id(14), now, uri = video),
                    ),
                selectedJournalIds = listOf(id(1), id(2)),
            ),
        )
    }

    private fun seedRecordingTransferFixture(context: android.content.Context) {
        val final = File(context.filesDir, "audio_notes/transfer-recording.m4a")
        check(final.parentFile?.mkdirs() == true || final.parentFile?.isDirectory == true)
        check(final.createNewFile()) { "Recording transfer fixture already exists" }
        final.writeBytes("committed recording".encodeToByteArray())
        val inFlight = CrashSafeRecording.inFlightFile(final, isolateForBackup = true)
        inFlight.writeBytes("unfinished raw recording".encodeToByteArray())
        CrashSafeRecording.remuxStagingFile(inFlight, final).writeBytes("unfinished remux".encodeToByteArray())
    }

    suspend fun verifyAndEdit() {
        verifyFixture()
        val now = Clock.System.now()
        if (notes.getNoteById(id(8)) == null) {
            notes.create(JournalNote.Text(id(8), now, now, "Written offline on replacement phone"), id(2))
        }
        assertEquals("Written offline on replacement phone", (notes.getNoteById(id(8)) as? JournalNote.Text)?.content)
    }

    suspend fun verifyFixture() {
        assertEquals("Native transfer journal", journals.getJournalById(id(1))?.title)
        assertEquals("Second transferred journal", journals.getJournalById(id(2))?.title)
        assertEquals("Text retained through native backup", (notes.getNoteById(id(3)) as? JournalNote.Text)?.content)
        assertNull(notes.getNoteById(id(7)), "deleted content reappeared")
        assertTrue(
            syncMetadata.getPendingUploads(EntityType.NOTE).any {
                it.entityId == id(7).toString() && it.operation == PendingOperation.DELETE
            },
            "The transferred deletion must remain queued for the next authenticated sync",
        )
        assertTrue(notes.getAllJournalNoteLinks().containsAll(listOf(id(1) to id(3), id(2) to id(3))))
        val image = assertNotNull(notes.getNoteById(id(4)) as? JournalNote.Image)
        val audio = assertNotNull(notes.getNoteById(id(5)) as? JournalNote.Audio)
        val video = assertNotNull(notes.getNoteById(id(6)) as? JournalNote.Video)
        verifyMedia(image.mediaRef, "proof.png")
        verifyMedia(audio.mediaRef, "proof.wav")
        verifyMedia(video.mediaRef, "proof.mp4")
        val draft = assertNotNull(journals.getDraft(id(10)))
        assertEquals(listOf(id(1), id(2)), draft.selectedJournalIds)
        assertEquals("Unfinished native-transfer draft", (draft.blocks[0] as? SerializableTextBlock)?.content)
        verifyMedia(assertNotNull((draft.blocks[1] as? SerializableImageBlock)?.uri), "proof.png")
        verifyMedia(assertNotNull((draft.blocks[2] as? SerializableAudioBlock)?.uri), "proof.wav")
        verifyMedia(assertNotNull((draft.blocks[3] as? SerializableVideoBlock)?.uri), "proof.mp4")
    }

    fun verifyUnfinishedCameraCapture(
        context: android.content.Context,
        inPlace: Boolean,
    ) {
        val capture = File(context.filesDir, "camera-captures/logdate-camera-unfinished.mp4")
        if (inPlace) {
            assertContentEquals(
                "unfinished camera output".encodeToByteArray(),
                capture.readBytes(),
                "In-place restore must retain excluded source data",
            )
        } else {
            assertFalse(capture.exists(), "unfinished CameraX output was transferred")
        }
    }

    suspend fun verifyPartialTransfer(expectedMissingMediaPath: String) {
        assertEquals("Native transfer journal", journals.getJournalById(id(1))?.title)
        assertEquals("Second transferred journal", journals.getJournalById(id(2))?.title)
        assertEquals("Text retained through native backup", (notes.getNoteById(id(3)) as? JournalNote.Text)?.content)
        assertNull(notes.getNoteById(id(7)), "deleted content reappeared")
        val image = assertNotNull(notes.getNoteById(id(4)) as? JournalNote.Image)
        val audio = assertNotNull(notes.getNoteById(id(5)) as? JournalNote.Audio)
        val video = assertNotNull(notes.getNoteById(id(6)) as? JournalNote.Video)
        val draft = assertNotNull(journals.getDraft(id(10)))
        assertEquals("Unfinished native-transfer draft", (draft.blocks[0] as? SerializableTextBlock)?.content)

        val mediaReferences =
            listOf(
                image.mediaRef to "proof.png",
                audio.mediaRef to "proof.wav",
                video.mediaRef to "proof.mp4",
                assertNotNull((draft.blocks[1] as? SerializableImageBlock)?.uri) to "proof.png",
                assertNotNull((draft.blocks[2] as? SerializableAudioBlock)?.uri) to "proof.wav",
                assertNotNull((draft.blocks[3] as? SerializableVideoBlock)?.uri) to "proof.mp4",
            )
        val missingFile =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                expectedMissingMediaPath.removePrefix("files/"),
            )
        assertFalse(missingFile.exists(), "The native transport unexpectedly delivered its deliberately omitted original")
        withTimeout(30_000) {
            while (
                mediaReferences.any { (reference, _) ->
                    !media.exists(reference) && !UnavailableMediaReferences.isUnavailable(reference)
                }
            ) {
                delay(50)
            }
        }
        val unavailable = mediaReferences.filter { (reference, _) -> UnavailableMediaReferences.isUnavailable(reference) }
        val available = mediaReferences.filterNot { (reference, _) -> UnavailableMediaReferences.isUnavailable(reference) }
        assertTrue(unavailable.isNotEmpty(), "The omitted original was not retained as recoverable media")
        assertTrue(available.isNotEmpty(), "The partial restore incorrectly marked every original unavailable")
        unavailable.forEach { (reference, _) ->
            assertNotNull(UnavailableMediaReferences.nativeSource(reference), "Missing original lost its source reference")
        }
        available.forEach { (reference, name) -> verifyMedia(reference, name) }

        assertNull(GlobalContext.get().get<SessionStorage>().getSession(), "Partial native restore unexpectedly recovered sign-in")
        val missingNote =
            listOf(
                Triple(image.uid, "image", image.mediaRef),
                Triple(audio.uid, "audio", audio.mediaRef),
                Triple(video.uid, "video", video.mediaRef),
            ).first { (_, _, reference) -> UnavailableMediaReferences.isUnavailable(reference) }
        val owner =
            GlobalContext
                .get()
                .get<CanonicalOwnerProvider>()
                .getCanonicalOwnerId()
        val origin =
            GlobalContext
                .get()
                .get<LogDateConfigRepository>()
                .getCurrentBackendUrl()
                .trimEnd('/')
        val result =
            GlobalContext.get().get<AutomaticMediaRecovery>().recover(
                missingNote.first,
                null,
                missingNote.second,
                missingNote.third,
                missingNote.third,
                UploadScope(owner, origin),
            )
        assertEquals(
            DiagnosticReason.SIGN_IN_REQUIRED,
            assertIs<AutomaticMediaRecoveryResult.Pending>(result).reason,
            "Unavailable originals must remain pending until their cloud recovery scope is available",
        )
    }

    private suspend fun save(
        name: String,
        type: String,
    ): String {
        val bytes = asset(name)
        return media.saveMedia(MediaPayload(name, type, bytes.size.toLong(), bytes))
    }

    private suspend fun verifyMedia(
        reference: String,
        name: String,
    ) {
        val bytes = media.readMedia(reference).data
        assertContentEquals(asset(name), bytes, "original media bytes changed")
        if (name.endsWith(".png")) {
            assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } else {
            play(bytes, name.substringAfterLast('.'))
        }
    }

    private fun play(
        bytes: ByteArray,
        extension: String,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("native-playback", ".$extension", context.cacheDir)
        val player = MediaPlayer()
        try {
            file.writeBytes(bytes)
            val completion = CountDownLatch(1)
            player.setOnCompletionListener { completion.countDown() }
            player.setDataSource(file.absolutePath)
            player.prepare()
            assertTrue(player.duration > 0)
            player.start()
            assertTrue(completion.await(20, TimeUnit.SECONDS), "offline playback did not complete")
        } finally {
            player.release()
            file.delete()
        }
    }

    private fun asset(name: String): ByteArray =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("recovery/$name")
            .use { it.readBytes() }

    private fun id(number: Int): Uuid = Uuid.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")
}

private const val MAX_NATIVE_TRANSFER_FIXTURE_DATABASE_BYTES = 24L * 1024 * 1024
