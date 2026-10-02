package app.logdate.client.e2e

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Two-device acceptance probe. The host supplies a task-private androidTest asset, never secrets
 * in instrumentation arguments. Run create on one managed device and read on a different one while
 * the same PostgreSQL-backed local server stays up. This class skips ordinary test suites.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryAcceptanceProbeTest {
    @Test
    fun `a fresh second device recovers content and opens it offline`(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = InstrumentationRegistry.getArguments().getString(MODE_ARGUMENT)
        assumeTrue("run only in the two-device acceptance harness", mode == "create" || mode == "read")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            "recovery acceptance can run only on an Android emulator"
        }
        val fixture = readFixture()
        val koin = GlobalContext.get()
        val config = koin.get<LogDateConfigRepository>()
        koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
        require(fixture.serverOrigin.startsWith("http://10.0.2.2:")) { "acceptance server must be local" }
        config.updateBackendUrl(fixture.serverOrigin)
        assertEquals(fixture.serverOrigin, config.getCurrentBackendUrl(), "acceptance server selection failed")
        val descriptor = koin.get<ServerDiscoveryClient>().discoverServer(fixture.serverOrigin).getOrThrow()
        assertEquals(fixture.serverOrigin, descriptor.serverOrigin)
        assertTrue(
            descriptor.hasProtocolFeature(app.logdate.shared.model.ServerProtocolFeature.RICH_DRAFTS_V1),
            "persistent server does not advertise rich draft sync",
        )
        config.updateServerDescriptor(descriptor)

        val identity = koin.get<IdentityKeyManager>()
        val sessions = koin.get<SessionStorage>()
        val sync = koin.get<DefaultSyncManager>()
        val journals = koin.get<JournalRepository>()
        val notes = koin.get<JournalNotesRepository>()
        val links = koin.get<JournalContentRepository>()
        val media = koin.get<MediaManager>()
        val owner = koin.get<app.logdate.client.device.identity.CanonicalOwnerProvider>()
        assertFalse(identity.hasIdentityKey(), "managed device already has an identity")
        assertTrue(journals.allJournalsObserved.first().isEmpty(), "managed device already has journals")
        assertTrue(notes.allNotesObserved.first().isEmpty(), "managed device already has notes")
        assertTrue(journals.getAllDrafts().isEmpty(), "managed device already has drafts")

        assertFalse(owner.hasBoundOwner(), "managed device already belongs to an account")
        assertTrue(owner.adoptRemoteOwnerIfUninitialized(fixture.accountId))
        assertEquals(fixture.accountId, owner.getCanonicalOwnerId())
        identity.recoverIdentity(fixture.recoveryWords)
        sessions.saveSession(UserSession(fixture.accessToken, fixture.refreshToken, fixture.accountId))
        assertEquals(fixture.serverOrigin, sessions.getOriginBoundSession()?.origin)
        assertEquals(fixture.accountId, sessions.getOriginBoundSession()?.session?.accountId)

        if (mode == "create") {
            createFixture(fixture, journals, notes, links, media)
            awaitSettledSync(sync)
            notes.removeById(fixture.id("deletedNote"))
            journals.deleteDraft(fixture.id("deletedDraft"))
            awaitSettledSync(sync)
            assertEquals(null, notes.getNoteById(fixture.id("deletedNote")))
            assertEquals(null, journals.getDraft(fixture.id("deletedDraft")))
        } else {
            awaitSettledSync(sync)
            assertRecovered(fixture, journals, notes, links, media)
            val device = UiDevice.getInstance(instrumentation)
            try {
                device.executeShellCommand("cmd connectivity airplane-mode enable")
                device.executeShellCommand("svc wifi disable")
                device.executeShellCommand("svc data disable")
                var networkChecksRemaining = 20
                while (networkChecksRemaining > 0 && serverReachable(fixture.serverOrigin)) {
                    delay(500)
                    networkChecksRemaining--
                }
                assertFalse(serverReachable(fixture.serverOrigin), "network is still available")
                assertRecovered(fixture, journals, notes, links, media)
                assertOfflineAttachmentsOpen(fixture, notes, journals, media)
            } finally {
                device.executeShellCommand("cmd connectivity airplane-mode disable")
                device.executeShellCommand("svc data enable")
                device.executeShellCommand("svc wifi enable")
            }
        }
    }

    private suspend fun createFixture(
        fixture: Fixture,
        journals: JournalRepository,
        notes: JournalNotesRepository,
        links: JournalContentRepository,
        media: MediaManager,
    ) {
        val now = Clock.System.now()
        val primary = Journal(id = fixture.id("journalPrimary"), title = fixture.primaryTitle)
        val secondary = Journal(id = fixture.id("journalSecondary"), title = fixture.secondaryTitle)
        journals.create(primary)
        journals.create(secondary)

        val imageRef = media.saveMedia(payload("recovery/proof.png", "image/png"))
        val audioRef = media.saveMedia(payload("recovery/proof.wav", "audio/wav"))
        val videoRef = media.saveMedia(payload("recovery/proof.mp4", "video/mp4"))
        val draftImageRef = media.saveMedia(payload("recovery/draft.png", "image/png"))
        val draftVideoRef = media.saveMedia(payload("recovery/draft.mp4", "video/mp4"))

        val text = JournalNote.Text(
            uid = fixture.id("textNote"), creationTimestamp = now, lastUpdated = now, content = fixture.text,
        )
        notes.create(text, primary.id)
        links.addContentToJournal(text.uid, secondary.id)
        notes.create(
            JournalNote.Image(
                uid = fixture.id("imageNote"), creationTimestamp = now, lastUpdated = now,
                mediaRef = imageRef, caption = fixture.imageCaption,
            ),
            primary.id,
        )
        notes.create(
            JournalNote.Audio(
                uid = fixture.id("audioNote"), creationTimestamp = now, lastUpdated = now,
                mediaRef = audioRef, durationMs = 500L,
            ),
            secondary.id,
        )
        notes.create(
            JournalNote.Video(
                uid = fixture.id("videoNote"), creationTimestamp = now, lastUpdated = now,
                mediaRef = videoRef, caption = fixture.videoCaption,
            ),
            primary.id,
        )
        notes.create(
            JournalNote.Text(
                uid = fixture.id("deletedNote"), creationTimestamp = now, lastUpdated = now,
                content = "deleted-${fixture.marker}",
            ),
            primary.id,
        )

        journals.saveDraft(
            EditorDraft(
                id = fixture.id("richDraft"),
                blocks = listOf(
                    SerializableTextBlock(id = fixture.id("richTextBlock"), timestamp = now, content = fixture.draftText),
                    SerializableImageBlock(id = fixture.id("richImageBlock"), timestamp = now, uri = imageRef),
                    SerializableAudioBlock(id = fixture.id("richAudioBlock"), timestamp = now, uri = audioRef),
                    SerializableVideoBlock(id = fixture.id("richVideoBlock"), timestamp = now, uri = videoRef),
                ),
                selectedJournalIds = listOf(primary.id, secondary.id),
            ),
        )
        journals.saveDraft(
            EditorDraft(
                id = fixture.id("mediaDraft"),
                blocks = listOf(
                    SerializableImageBlock(id = fixture.id("mediaImageBlock"), timestamp = now, uri = draftImageRef),
                    SerializableVideoBlock(id = fixture.id("mediaVideoBlock"), timestamp = now, uri = draftVideoRef),
                ),
                selectedJournalIds = listOf(secondary.id),
            ),
        )
        journals.saveDraft(
            EditorDraft(
                id = fixture.id("deletedDraft"),
                blocks = listOf(
                    SerializableTextBlock(id = fixture.id("deletedDraftBlock"), timestamp = now, content = "delete me"),
                ),
                selectedJournalIds = listOf(primary.id),
            ),
        )
    }

    private suspend fun assertRecovered(
        fixture: Fixture,
        journals: JournalRepository,
        notes: JournalNotesRepository,
        links: JournalContentRepository,
        media: MediaManager,
    ) {
        assertEquals(fixture.primaryTitle, journals.getJournalById(fixture.id("journalPrimary"))?.title)
        assertEquals(fixture.secondaryTitle, journals.getJournalById(fixture.id("journalSecondary"))?.title)
        assertEquals(fixture.text, (notes.getNoteById(fixture.id("textNote")) as? JournalNote.Text)?.content)
        val image = notes.getNoteById(fixture.id("imageNote")) as? JournalNote.Image
        val audio = notes.getNoteById(fixture.id("audioNote")) as? JournalNote.Audio
        val video = notes.getNoteById(fixture.id("videoNote")) as? JournalNote.Video
        assertEquals(fixture.imageCaption, image?.caption)
        assertEquals(fixture.videoCaption, video?.caption)
        assertNotNull(image)
        assertNotNull(audio)
        assertNotNull(video)
        assertMediaHash(media, image.mediaRef, "recovery/proof.png")
        assertMediaHash(media, audio.mediaRef, "recovery/proof.wav")
        assertMediaHash(media, video.mediaRef, "recovery/proof.mp4")

        val expectedLinks = setOf(
            fixture.id("journalPrimary") to fixture.id("textNote"),
            fixture.id("journalSecondary") to fixture.id("textNote"),
            fixture.id("journalPrimary") to fixture.id("imageNote"),
            fixture.id("journalSecondary") to fixture.id("audioNote"),
            fixture.id("journalPrimary") to fixture.id("videoNote"),
        )
        assertEquals(expectedLinks, notes.getAllJournalNoteLinks().toSet())
        assertEquals(
            setOf(fixture.id("journalPrimary"), fixture.id("journalSecondary")),
            links.observeJournalsForContent(fixture.id("textNote")).first().map { it.id }.toSet(),
        )
        val richDraft = assertNotNull(journals.getDraft(fixture.id("richDraft")))
        assertEquals(
            setOf(fixture.id("journalPrimary"), fixture.id("journalSecondary")),
            richDraft.selectedJournalIds.toSet(),
        )
        assertEquals(fixture.draftText, (richDraft.blocks[0] as? SerializableTextBlock)?.content)
        assertEquals(
            listOf("richTextBlock", "richImageBlock", "richAudioBlock", "richVideoBlock").map(fixture::id),
            richDraft.blocks.map { it.id },
        )
        assertMediaHash(media, assertNotNull((richDraft.blocks[1] as? SerializableImageBlock)?.uri), "recovery/proof.png")
        assertMediaHash(media, assertNotNull((richDraft.blocks[2] as? SerializableAudioBlock)?.uri), "recovery/proof.wav")
        assertMediaHash(media, assertNotNull((richDraft.blocks[3] as? SerializableVideoBlock)?.uri), "recovery/proof.mp4")

        val mediaDraft = assertNotNull(journals.getDraft(fixture.id("mediaDraft")))
        assertEquals(listOf(fixture.id("journalSecondary")), mediaDraft.selectedJournalIds)
        assertEquals(
            listOf("mediaImageBlock", "mediaVideoBlock").map(fixture::id),
            mediaDraft.blocks.map { it.id },
        )
        assertMediaHash(media, assertNotNull((mediaDraft.blocks[0] as? SerializableImageBlock)?.uri), "recovery/draft.png")
        assertMediaHash(media, assertNotNull((mediaDraft.blocks[1] as? SerializableVideoBlock)?.uri), "recovery/draft.mp4")
        assertEquals(null, notes.getNoteById(fixture.id("deletedNote")))
        assertEquals(null, journals.getDraft(fixture.id("deletedDraft")))
    }

    private suspend fun awaitSettledSync(sync: DefaultSyncManager) {
        val errorTypes = mutableSetOf<app.logdate.client.sync.SyncErrorType>()
        val safeApiCodes = mutableSetOf<String>()
        var lastStatus = sync.getSyncStatus()
        repeat(20) {
            val result = sync.fullSync()
            errorTypes += result.errors.map { it.type }
            safeApiCodes += result.errors.mapNotNull { safeApiErrorCode(it.cause) }
            lastStatus = sync.getSyncStatus()
            if (result.success && lastStatus.pendingUploads == 0 && lastStatus.pendingDownloads == 0) return
            delay(1_000)
        }
        error(
            "sync did not settle; types=${errorTypes.map { it.name }.sorted()}; " +
                "apiCodes=${safeApiCodes.sorted()}; " +
                "pendingUploads=${lastStatus.pendingUploads}; pendingDownloads=${lastStatus.pendingDownloads}",
        )
    }

    private fun safeApiErrorCode(error: Throwable?): String? {
        val code = (error as? CloudApiException)?.errorCode ?: return null
        // The probe reports only the bounded protocol code token, never an error message or body.
        return code.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) } ?: "UNRECOGNIZED_API_ERROR"
    }

    private suspend fun assertMediaHash(media: MediaManager, ref: String, asset: String) {
        assertTrue(media.exists(ref), "recovered media is missing")
        assertEquals(sha256(assetBytes(asset)), sha256(media.readMedia(ref).data), "decrypted media bytes changed")
    }

    private suspend fun assertOfflineAttachmentsOpen(
        fixture: Fixture,
        notes: JournalNotesRepository,
        journals: JournalRepository,
        media: MediaManager,
    ) {
        val image = assertNotNull(notes.getNoteById(fixture.id("imageNote")) as? JournalNote.Image)
        val audio = assertNotNull(notes.getNoteById(fixture.id("audioNote")) as? JournalNote.Audio)
        val video = assertNotNull(notes.getNoteById(fixture.id("videoNote")) as? JournalNote.Video)
        val richDraft = assertNotNull(journals.getDraft(fixture.id("richDraft")))
        val mediaDraft = assertNotNull(journals.getDraft(fixture.id("mediaDraft")))
        val richDraftImage = assertNotNull((richDraft.blocks[1] as? SerializableImageBlock)?.uri)
        val richDraftAudio = assertNotNull((richDraft.blocks[2] as? SerializableAudioBlock)?.uri)
        val richDraftVideo = assertNotNull((richDraft.blocks[3] as? SerializableVideoBlock)?.uri)
        val draftImage = assertNotNull((mediaDraft.blocks[0] as? SerializableImageBlock)?.uri)
        val draftVideo = assertNotNull((mediaDraft.blocks[1] as? SerializableVideoBlock)?.uri)
        for (ref in listOf(image.mediaRef, richDraftImage, draftImage)) {
            val bytes = media.readMedia(ref).data
            assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size), "offline image could not be decoded")
        }
        for (ref in listOf(audio.mediaRef, richDraftAudio)) {
            playOfflineMedia(media.readMedia(ref).data, ".wav")
        }
        for (ref in listOf(video.mediaRef, richDraftVideo, draftVideo)) {
            playOfflineMedia(media.readMedia(ref).data, ".mp4")
        }
    }

    private fun playOfflineMedia(bytes: ByteArray, extension: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("recovery-playback-", extension, context.cacheDir)
        try {
            source.outputStream().use { it.write(bytes) }
            val player = MediaPlayer()
            try {
                val completed = CountDownLatch(1)
                val failed = AtomicBoolean(false)
                player.setOnCompletionListener { completed.countDown() }
                player.setOnErrorListener { _, _, _ ->
                    failed.set(true)
                    completed.countDown()
                    true
                }
                player.setDataSource(source.absolutePath)
                player.prepare()
                assertTrue(player.duration > 0, "offline media has no playable duration")
                player.start()
                assertTrue(completed.await(20, TimeUnit.SECONDS), "offline media playback did not complete")
                assertFalse(failed.get(), "offline media decoder reported an error")
            } finally {
                player.release()
            }
        } finally {
            source.delete()
        }
    }

    private fun payload(asset: String, mimeType: String): MediaPayload {
        val bytes = assetBytes(asset)
        return MediaPayload(asset.substringAfterLast('/'), mimeType, bytes.size.toLong(), bytes)
    }

    private fun assetBytes(path: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(path).use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun serverReachable(origin: String): Boolean = try {
        val connection = URL("$origin/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        try { connection.responseCode in 200..499 } finally { connection.disconnect() }
    } catch (_: Exception) { false }

    private fun readFixture(): Fixture {
        val raw = assetBytes(PRIVATE_ASSET).decodeToString()
        val json = JSONObject(raw)
        val ids = json.getJSONObject("ids")
        val recovery = json.getJSONArray("recoveryWords")
        return Fixture(
            serverOrigin = json.getString("serverOrigin"),
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            accountId = json.getString("accountId"),
            recoveryWords = (0 until recovery.length()).map(recovery::getString),
            marker = json.getString("marker"),
            ids = (ids.keys().asSequence()).associateWith(ids::getString),
        )
    }

    private data class Fixture(
        val serverOrigin: String,
        val accessToken: String,
        val refreshToken: String,
        val accountId: String,
        val recoveryWords: List<String>,
        val marker: String,
        val ids: Map<String, String>,
    ) {
        val primaryTitle = "Recovery primary $marker"
        val secondaryTitle = "Recovery secondary $marker"
        val text = "Encrypted text $marker"
        val draftText = "Rich unfinished entry $marker"
        val imageCaption = "Image caption $marker"
        val videoCaption = "Video caption $marker"
        fun id(name: String): Uuid = Uuid.parse(requireNotNull(ids[name]) { "missing fixture id" })
    }

    private companion object {
        const val MODE_ARGUMENT = "logdate.recoveryAcceptanceMode"
        const val PRIVATE_ASSET = "recovery-acceptance.json"
    }
}
