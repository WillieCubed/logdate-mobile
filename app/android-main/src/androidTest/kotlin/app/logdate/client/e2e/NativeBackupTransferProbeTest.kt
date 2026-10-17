package app.logdate.client.e2e

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.encryption.PassphraseBackupStore
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import kotlinx.coroutines.runBlocking
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
                mode in setOf("seed", "verify", "quota", "quota-cleanup", "missing-keys", "compact-transfer"),
            )
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
            require(instrumentation.targetContext.packageName.endsWith(".debug")) { "Isolated debug application required" }
            val context = instrumentation.targetContext
            ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
            val koin = GlobalContext.get()
            if (mode == "missing-keys") {
                verifyMissingRecoveryKeys(context, koin)
                return@runBlocking
            }
            val fixture = NativeJournalFixture(koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
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
                val mediaCiphertext =
                    crypto.aesGcmEncrypt(
                        mediaKey,
                        ByteArray(12),
                        "native-transfer".encodeToByteArray(),
                        "cloud media payload".encodeToByteArray(),
                    )
                fixture.seed()
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
                        .put("mediaCiphertext", Base64.encode(mediaCiphertext))
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
                assertNotEquals(
                    arguments.getString("sourceDevice"),
                    koin
                        .get<DeviceIdProvider>()
                        .getDeviceId()
                        .value
                        .toString(),
                )
                assertEquals(
                    arguments.getString("sourceFingerprint"),
                    IdentityKeyFingerprint.of(identity.getIdentityKey(), koin.get<CryptoManager>()),
                )
                assertContentEquals(
                    "cloud media payload".encodeToByteArray(),
                    crypto.aesGcmDecrypt(
                        koin.get<MediaPayloadKeyProvider>().getOrCreateKey(),
                        ByteArray(12),
                        "native-transfer".encodeToByteArray(),
                        Base64.decode(requireNotNull(arguments.getString("sourceMediaCiphertext"))),
                    ),
                    "Transferred encryption keys cannot decrypt the original Cloud media payload",
                )
                fixture.verifyAndEdit()
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
    assertNull(koin.get<SecureStorage>().getString("db_encryption_key"), "Startup persisted a replacement database key")
    assertTrue(databaseFile.isFile, "Recovery handling deleted the retained encrypted journal")
    assertContentEquals(
        originalDatabaseDigest,
        MessageDigest.getInstance("SHA-256").digest(databaseFile.readBytes()),
        "Recovery handling changed the retained encrypted journal",
    )
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

    suspend fun seed() {
        assertNull(journals.getJournalById(id(1)), "fixture already exists; use fresh emulator data")
        journals.create(Journal(id = id(1), title = "Native transfer journal"))
        journals.create(Journal(id = id(2), title = "Second transferred journal"))
        val now = Clock.System.now()
        notes.create(JournalNote.Text(id(3), now, now, "Text retained through native backup"), id(1))
        links.addContentToJournal(id(3), id(2))
        val image = save("proof.png", "image/png")
        val audio = save("proof.wav", "audio/wav")
        val video = save("proof.mp4", "video/mp4")
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

    suspend fun verifyAndEdit() {
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
        val now = Clock.System.now()
        if (notes.getNoteById(id(8)) == null) {
            notes.create(JournalNote.Text(id(8), now, now, "Written offline on replacement phone"), id(2))
        }
        assertEquals("Written offline on replacement phone", (notes.getNoteById(id(8)) as? JournalNote.Text)?.content)
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
