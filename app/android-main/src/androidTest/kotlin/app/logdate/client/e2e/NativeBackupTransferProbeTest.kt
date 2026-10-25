package app.logdate.client.e2e

import android.app.job.JobScheduler
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.data.notes.drafts.AndroidLocalEntryDraftStore
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.NativeJournalSnapshotScope
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.database.verifyNativeJournalDatabaseSnapshot
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.device.restore.verifyNativeJournalMediaInventory
import app.logdate.client.device.restore.verifyNativeJournalSourceReceipt
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.getBytes
import app.logdate.client.device.storage.putBytes
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.shared.model.SerializableTextBlock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
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
                        "verify-restored",
                        "quota",
                        "quota-cleanup",
                        "missing-keys",
                        "missing-keys-rejected",
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
                        "draft-only-source",
                        "pending-camera-source",
                    ),
            )
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
            require(instrumentation.targetContext.packageName.endsWith(".debug")) { "Isolated debug application required" }
            val context = instrumentation.targetContext
            if (mode == "draft-only-source") {
                verifyDraftOnlyNativeSource(context)
                return@runBlocking
            }
            if (mode == "pending-camera-source") {
                verifyPendingCameraSource(context)
                return@runBlocking
            }
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
                val ownerProvider = koin.get<CanonicalOwnerProvider>()
                val owner = ownerProvider.getCanonicalOwnerId()
                assertTrue(ownerProvider.adoptRemoteOwnerIfUninitialized(owner))
                sessions.saveExplicitSession(
                    nativeSourceSession(requireNotNull(InstrumentationRegistry.getArguments().getString("sourceSessionId")), owner),
                )
                assertEquals(owner, sessions.getSession()?.accountId)
                val drafts = koin.get<JournalRepository>().getAllDrafts()
                val draft = assertNotNull(drafts.singleOrNull { draft -> draft.id == Uuid.parse("00000000-0000-0000-0000-000000000010") })
                assertEquals("Unfinished native-transfer draft", (draft.blocks.first() as? SerializableTextBlock)?.content)
                assertEquals(4, draft.blocks.size, "Account adoption must retain the draft's original media blocks")
                return@runBlocking
            }
            if (mode == "source-account-session-cleanup") {
                val sessions = koin.get<SessionStorage>()
                val expected =
                    nativeSourceSession(
                        requireNotNull(InstrumentationRegistry.getArguments().getString("sourceSessionId")),
                        koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId(),
                    )
                if (!clearNativeSourceSession(sessions, expected)) return@runBlocking
                assertNull(sessions.getSession(), "Synthetic data-only source session was not cleared")
                assertTrue(
                    koin.get<JournalRepository>().getAllDrafts().any { draft ->
                        draft.blocks.filterIsInstance<SerializableTextBlock>().any { it.content == "Unfinished native-transfer draft" }
                    },
                    "Clearing the source session must retain access to the adopted journal draft",
                )
                return@runBlocking
            }
            if (mode == "source-backup-integrity") {
                val arguments = InstrumentationRegistry.getArguments()
                if (arguments.getString("sourceInstallationTrustFixture") == "true") {
                    NativeInstallationTrustFixture(koin).verifySource(requireNotNull(arguments.getString("sourceDevice")))
                }
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
                    val expected =
                        nativeSourceSession(
                            requireNotNull(arguments.getString("sourceSessionId")),
                            requireNotNull(arguments.getString("sourceOwner")),
                        )
                    assertEquals(expected, session)
                    assertEquals(arguments.getString("sourceOwner"), session.accountId)
                } else {
                    assertFalse(
                        sessions.hasValidSession(),
                        "Native backup restored a source account session that should be absent",
                    )
                    assertNull(sessions.getSession(), "Native backup unexpectedly created a source account session")
                }
                val sourceFixture = NativeJournalFixture(koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
                sourceFixture.verifyFixture()
                verifyRecordingTransferFixture(context, inPlace = true, expectation = RecordingTransferExpectation.SOURCE)
                sourceFixture.verifyUnfinishedCameraCapture(context, inPlace = true)
                return@runBlocking
            }
            if (mode == "missing-keys") {
                verifyMissingRecoveryKeys(context, koin)
                return@runBlocking
            }
            if (mode == "missing-keys-rejected") {
                verifyRejectedMissingRecoveryKeys(context, koin)
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
                assertEquals(
                    PostRestoreType.CONTENT_MISSING,
                    PostRestoreDetector(context).detect(isOnboarded = true),
                    "Restored credentials cannot mark missing journal content as recovered",
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
                fixture.seed(
                    context,
                    includePendingCameraDraft = InstrumentationRegistry.getArguments().getString("pendingCameraDraftFixture") == "true",
                )
                val installationTrust = InstrumentationRegistry.getArguments().getString("nativeInstallationTrustFixture") == "true"
                if (installationTrust) NativeInstallationTrustFixture(koin).seed()
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
                        .put("installationTrustFixture", installationTrust)
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
                if (arguments.getString("sourceInstallationTrustFixture") == "true") {
                    val trust = NativeInstallationTrustFixture(koin)
                    val sourceDevice = requireNotNull(arguments.getString("sourceDevice"))
                    if (mode == "verify-in-place") trust.verifySource(sourceDevice) else trust.verifyDestination(sourceDevice)
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
                val recordingExpectation =
                    if (arguments.getString("sourceRecordingTransferFixture") == "true") {
                        when (arguments.getString("sourceRecordingTransport")) {
                            "cloud" -> RecordingTransferExpectation.CLOUD_TARGET
                            "d2d" -> RecordingTransferExpectation.DEVICE_TRANSFER
                            else -> error("Recording restore probe requires its selected Android transport")
                        }
                    } else {
                        null
                    }
                val retainedD2dEdit =
                    mode == "verify-in-place" && recordingExpectation == RecordingTransferExpectation.DEVICE_TRANSFER
                val expectedJournalTitle =
                    if (retainedD2dEdit) "Mutated after native D2D backup" else "Native transfer journal"
                if (retainedD2dEdit) {
                    val retainedJournal =
                        koin.get<JournalRepository>().getJournalById(Uuid.parse("00000000-0000-0000-0000-000000000001"))
                    assertEquals(
                        expectedJournalTitle,
                        retainedJournal?.title,
                        "In-place D2D restore must retain the accepted journal edit",
                    )
                }
                if (recordingExpectation != null) {
                    verifyRecordingTransferFixture(context, inPlace = mode == "verify-in-place", expectation = recordingExpectation)
                }
                fixture.verifyUnfinishedCameraCapture(context, inPlace = mode == "verify-in-place")
                if (mode == "partial-media") {
                    fixture.verifyPartialTransfer(requireNotNull(arguments.getString("expectedMissingMediaPath")))
                    return@runBlocking
                }
                val originalMediaIncluded = recordingExpectation != RecordingTransferExpectation.CLOUD_TARGET
                if (mode == "verify-restored") {
                    fixture.verifyFixture(originalMediaIncluded)
                } else {
                    fixture.verifyAndEdit(
                        originalMediaIncluded,
                        expectedJournalTitle = expectedJournalTitle,
                    )
                }
                if (arguments.getString("pendingCameraDraftFixture") == "true") {
                    verifyPendingCameraTransferDraft(context)
                }
            }
        }

    /** The harness selects this on a fresh managed device, before normal startup opens Room. */
    private suspend fun verifyDraftOnlyNativeSource(context: android.content.Context) {
        val database = context.getDatabasePath("logdate")
        assertFalse(database.exists(), "Draft-only source must begin before the first journal open")
        val owner = Uuid.random().toString()
        createAndroidMainDataStore(context).edit { preferences ->
            preferences[stringPreferencesKey("identity.canonical_owner_id")] = owner
            preferences[stringPreferencesKey("backend_url")] = "https://cloud.logdate.app"
        }
        val directory = File(context.filesDir, "user_media").apply { check(mkdirs() || isDirectory) }
        val original = File(directory, "draft-only-original.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        File(directory, "unreferenced-original.png").writeBytes(byteArrayOf(5, 6, 7, 8))
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
        AndroidLocalEntryDraftStore(context).saveDraft(
            EntryDraft(
                Uuid.random(),
                listOf(JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = original.toURI().toString())),
                now,
                now,
            ),
        )
        val snapshotClass = Class.forName("app.logdate.client.data.restore.NativeJournalTransferSnapshot")
        val snapshot =
            snapshotClass
                .getDeclaredConstructor(
                    android.content.Context::class.java,
                ).apply { isAccessible = true }
                .newInstance(context)
        try {
            snapshotClass.getDeclaredMethod("prepare").apply { isAccessible = true }.invoke(snapshot)
            val current = File(context.filesDir, "native_journal_transfer/current")
            val source = File(current, "database/logdate")
            val preferences = File(current, "files/logdate.preferences_pb")
            val receipt =
                verifyNativeJournalSourceReceipt(
                    File(current, NATIVE_SOURCE_RECEIPT_PATH),
                    preferences,
                    mapOf("database/logdate" to source),
                )
            val key = checkNotNull(PassphraseBackupStore(context).read())
            try {
                assertEquals(
                    NativeJournalSnapshotScope(owner, false, "https://cloud.logdate.app", receipt.preferencesSha256),
                    verifyNativeJournalDatabaseSnapshot(source, key),
                )
            } finally {
                key.fill(0)
            }
            val inventory =
                verifyNativeJournalMediaInventory(
                    File(current, NATIVE_MEDIA_INVENTORY_PATH),
                    checkNotNull(receipt.mediaInventorySha256),
                )
            assertNotNull(inventory.entry("files/user_media/draft-only-original.png"))
            assertNull(inventory.entry("files/user_media/unreferenced-original.png"))
            val emitted = mutableListOf<File>()
            snapshotClass
                .getDeclaredMethod("backupSelectedMedia", Function1::class.java)
                .apply { isAccessible = true }
                .invoke(snapshot, { file: File -> emitted += file })
            assertEquals(listOf(original.canonicalFile), emitted.map(File::getCanonicalFile))
        } finally {
            snapshotClass.getDeclaredMethod("finish").apply { isAccessible = true }.invoke(snapshot)
        }
    }

    private fun verifyPendingCameraSource(context: android.content.Context) {
        val snapshotClass = Class.forName("app.logdate.client.data.restore.NativeJournalTransferSnapshot")
        val snapshot =
            snapshotClass
                .getDeclaredConstructor(android.content.Context::class.java)
                .apply { isAccessible = true }
                .newInstance(context)
        try {
            snapshotClass.getDeclaredMethod("prepare").apply { isAccessible = true }.invoke(snapshot)
            val current = File(context.filesDir, "native_journal_transfer/current")
            val database = File(current, "database/logdate")
            val preferences = File(current, "files/logdate.preferences_pb")
            val receipt =
                verifyNativeJournalSourceReceipt(
                    File(current, NATIVE_SOURCE_RECEIPT_PATH),
                    preferences,
                    mapOf("database/logdate" to database),
                )
            val sourceDrafts = File(context.filesDir, "datastore/entry_drafts.preferences_pb")
            val pinnedDrafts = File(current, "files/datastore/entry_drafts.preferences_pb")
            assertNotNull(receipt.entryDraftsSha256)
            assertContentEquals(sourceDrafts.readBytes(), pinnedDrafts.readBytes())
            val inventory =
                verifyNativeJournalMediaInventory(
                    File(current, NATIVE_MEDIA_INVENTORY_PATH),
                    assertNotNull(receipt.mediaInventorySha256),
                )
            val cameraSources =
                listOf(
                    "files/camera-captures/pending-camera-transfer.jpg",
                    "files/camera-captures/pending-camera-transfer.mp4",
                ).associateWith { path ->
                    val source = File(context.filesDir, path.removePrefix("files/"))
                    val pinned = File(current, path)
                    assertNotNull(inventory.entry(path))
                    assertContentEquals(source.readBytes(), pinned.readBytes())
                    source to source.readBytes()
                }
            cameraSources.values.forEach { (source, _) -> source.writeText("changed after snapshot") }
            val emitted = mutableListOf<File>()
            try {
                snapshotClass
                    .getDeclaredMethod("backupSelectedMedia", Function1::class.java)
                    .apply { isAccessible = true }
                    .invoke(snapshot, { file: File -> emitted += file })
                assertTrue(emitted.none { it.path.contains("/camera-captures/") }, "Pinned camera originals must not be emitted twice")
            } finally {
                cameraSources.values.forEach { (source, bytes) -> source.writeBytes(bytes) }
            }
        } finally {
            snapshotClass.getDeclaredMethod("finish").apply { isAccessible = true }.invoke(snapshot)
        }
    }

    private suspend fun verifyPendingCameraTransferDraft(context: android.content.Context) {
        ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
        val draftsStore = AndroidLocalEntryDraftStore(context)

        suspend fun custodyDiagnostics(): String {
            val observed = draftsStore.getAllDrafts()
            val retained = NativeJournalReplayCustody(context).sealedPayloads()
            val admittedCount =
                File(context.noBackupFilesDir, "native-journal-staged-sources")
                    .listFiles()
                    .orEmpty()
                    .count { it.isDirectory && File(it, "admitted").isFile }
            val pendingJob = context.getSystemService(JobScheduler::class.java).getPendingJob(0x4e4a5235) != null
            return "cameraDrafts=${observed.count { draft ->
                draft.pendingMedia.any { it.mediaType == PendingMediaType.CAMERA_PHOTO || it.mediaType == PendingMediaType.CAMERA_VIDEO }
            }}, " +
                "retainedFiles=${retained.size}, hasReceipt=${retained.any { it.path.endsWith("native_source_scope.json") }}, " +
                "hasDrafts=${retained.any { it.path.endsWith("entry_drafts.preferences_pb") }}, " +
                "hasInventory=${retained.any { it.path.endsWith("native_media_inventory.json") }}, " +
                "cameraFileCount=${retained.count { it.path.contains("/camera-captures/") }}, " +
                "admittedSourceCount=$admittedCount, retainedReconciliationPending=$pendingJob, " +
                "stageMarker=${File(context.noBackupFilesDir, "native-journal-transfer-staged").exists()}"
        }
        val cameraDrafts =
            withTimeoutOrNull(90_000) {
                delay(10_000)
                Napier.i("Pending camera admission after 10s: ${custodyDiagnostics()}", tag = "NativeBackupTransferProbe")
                while (true) {
                    val drafts =
                        draftsStore.getAllDrafts().filter { draft ->
                            draft.pendingMedia.any {
                                it.mediaType == PendingMediaType.CAMERA_PHOTO || it.mediaType == PendingMediaType.CAMERA_VIDEO
                            }
                        }
                    if (drafts.size >= 2) return@withTimeoutOrNull drafts
                    delay(100)
                }
                error("unreachable")
            } ?: run {
                error(
                    "Pending camera draft admission timed out: ${custodyDiagnostics()}",
                )
            }
        val sourceId = Uuid.parse("00000000-0000-0000-0000-000000000020")
        val sourceDraft = assertNotNull(cameraDrafts.singleOrNull { it.id == sourceId }, "Source camera draft was not restored")
        val imported = assertNotNull(cameraDrafts.singleOrNull { it.id != sourceId }, "Pending camera draft was not admitted exactly once")
        assertEquals(2, cameraDrafts.size, "Camera custody must leave one source draft and one imported draft")
        assertEquals(2, imported.pendingMedia.size)
        val expectedContents =
            mapOf(
                PendingMediaType.CAMERA_PHOTO to "pending camera original",
                PendingMediaType.CAMERA_VIDEO to "pending camera video original",
            )
        imported.pendingMedia.forEach { record ->
            val restoredPath = assertNotNull(record.filePath)
            val sourcePath = assertNotNull(sourceDraft.pendingMedia.single { it.mediaType == record.mediaType }.filePath)
            assertNotEquals(sourcePath, restoredPath, "Imported draft retained the source-device path")
            assertTrue(restoredPath.startsWith(File(context.filesDir, "camera-captures").absolutePath))
            val restoredOriginal = File(restoredPath)
            assertTrue(restoredOriginal.isFile, "Recovered camera original is missing")
            assertContentEquals(requireNotNull(expectedContents[record.mediaType]).encodeToByteArray(), restoredOriginal.readBytes())
        }
    }
}
