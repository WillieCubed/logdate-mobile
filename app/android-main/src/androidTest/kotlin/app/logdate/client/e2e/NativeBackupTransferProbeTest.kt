package app.logdate.client.e2e

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
            assumeTrue("requires the native backup acceptance harness", mode == "seed" || mode == "verify" || mode == "quota")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Emulator required" }
            require(instrumentation.targetContext.packageName.endsWith(".debug")) { "Isolated debug application required" }
            val koin = GlobalContext.get()
            val fixture = NativeJournalFixture(koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
            if (mode == "quota") {
                require(
                    File(instrumentation.targetContext.noBackupFilesDir, "native-backup-fixture.json").isFile,
                ) { "Synthetic source fixture required" }
                fixture.exceedCloudQuota()
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

private class NativeJournalFixture(
    private val journals: JournalRepository,
    private val notes: JournalNotesRepository,
    private val links: JournalContentRepository,
    private val media: MediaManager,
    private val syncMetadata: SyncMetadataService,
) {
    suspend fun exceedCloudQuota() {
        assertEquals("Native transfer journal", journals.getJournalById(id(1))?.title)
        if (notes.getNoteById(id(999)) != null) return
        val now = Clock.System.now()
        notes.create(JournalNote.Text(id(999), now, now, "q".repeat(28 * 1024 * 1024)), id(1))
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
