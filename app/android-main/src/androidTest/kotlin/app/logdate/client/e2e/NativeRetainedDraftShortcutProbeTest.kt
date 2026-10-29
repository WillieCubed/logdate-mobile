package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutDescriptor
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutPublisher
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.resolveMainActivityNavKey
import app.logdate.client.shortcuts.AndroidDynamicShortcutApplier
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.metadata.MediaSyncRef
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.feature.editor.navigation.EntryEditorRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Uses the current editor's persisted EntryDraft, separately from legacy archive drafts. */
@RunWith(AndroidJUnit4::class)
class NativeRetainedDraftShortcutProbeTest {
    @Test
    fun retainedDraftOpensFromItsPublishedShortcut() =
        runBlocking {
            val stage = InstrumentationRegistry.getArguments().getString("retainedDraftShortcutStage")
            assumeTrue(stage != null)
            require(stage in setOf("seed", "upload", "verify"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            require(context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            val koin = GlobalContext.get()
            val drafts = koin.get<EntryDraftRepository>()
            val id = fixtureId(21)
            val text = "Current editor draft retained through native transfer"
            if (stage == "seed") {
                require(drafts.getDrafts().first().none { it.id == id })
                val original = koin.get<JournalNotesRepository>().getNoteById(fixtureId(4)) as JournalNote.Image
                val created = Instant.fromEpochMilliseconds(1_600_000_000_000)
                val saved =
                    drafts.saveDraftSnapshot(
                        EntryDraft(
                            id = id,
                            notes =
                                listOf(
                                    JournalNote.Text(fixtureId(22), created, created, text),
                                    JournalNote.Image(
                                        uid = fixtureId(23),
                                        creationTimestamp = created,
                                        lastUpdated = created,
                                        mediaRef = original.mediaRef,
                                        caption = "Unfinished image caption",
                                    ),
                                ),
                            createdAt = created,
                            updatedAt = created,
                            selectedJournalIds = listOf(fixtureId(1), fixtureId(2)),
                        ),
                    )
                assertEquals(id, saved)
                return@runBlocking
            }
            if (stage == "upload") {
                koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                val sessions = koin.get<SessionStorage>()
                val credentials = readNativeDeviceEnrollmentCredentials(context)
                require(credentials.sourceDevice == null)
                val bound = requireNotNull(sessions.captureAuthenticationSnapshot()?.session)
                assertEquals(credentials.owner, bound.session.accountId)
                assertEquals(credentials.origin, bound.origin)
                val draftImage =
                    drafts
                        .getDraft(id)
                        .first()
                        .getOrThrow()
                        .notes
                        .single { it.uid == fixtureId(23) } as JournalNote.Image
                val committedImage = koin.get<JournalNotesRepository>().getNoteById(fixtureId(4)) as JournalNote.Image
                // Seed both server originals without draining the deliberately retained source outbox.
                for (image in listOf(draftImage, committedImage)) {
                    val upload =
                        koin
                            .get<CloudMediaDataSource>()
                            .uploadMedia(
                                bound.session.accessToken,
                                image.uid,
                                koin.get<MediaManager>().openMedia(image.mediaRef),
                            ).getOrThrow()
                    koin.get<MediaSyncRefStore>().upsert(
                        MediaSyncRef(
                            image.uid.toString(),
                            image.mediaRef,
                            upload.downloadUrl,
                            upload.mediaId,
                            Clock.System.now().toEpochMilliseconds(),
                            bound.origin,
                            bound.session.accountId,
                        ),
                    )
                }
                return@runBlocking
            }
            val original = drafts.getDraft(id).first().getOrThrow()
            val beforeIds =
                drafts
                    .getDrafts()
                    .first()
                    .map { it.id }
                    .toSet()
            assertEquals(Instant.fromEpochMilliseconds(1_600_000_000_000), original.createdAt)
            assertEquals(text, (original.notes.single { it.uid == fixtureId(22) } as JournalNote.Text).content)
            val image = original.notes.single { it.uid == fixtureId(23) } as JournalNote.Image
            val media = koin.get<MediaManager>()
            val transferredDigest = digest(media.readMedia(image.mediaRef).data)
            assertEquals(
                digest(
                    instrumentation.context.assets
                        .open("recovery/proof.png")
                        .use { it.readBytes() },
                ),
                transferredDigest,
            )
            val previousShortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
            try {
                repeat(2) {
                    val descriptors = koin.get<DynamicShortcutPublisher>().computeShortcuts()
                    val draft = descriptors.filterIsInstance<DynamicShortcutDescriptor.ContinueDraft>().single()
                    assertEquals(id, draft.draftId)
                    assertTrue(koin.get<AndroidDynamicShortcutApplier>().apply(descriptors))
                    val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == draft.id }
                    val intent = Intent(assertNotNull(shortcut.intent))
                    assertEquals(id.toString(), (resolveMainActivityNavKey(intent) as EntryEditorRoute).draftId)
                    ActivityScenario.launch<MainActivity>(intent).use {
                        val device = UiDevice.getInstance(instrumentation)
                        val deadline = SystemClock.uptimeMillis() + 30_000
                        var visible = false
                        while (SystemClock.uptimeMillis() < deadline) {
                            if (device.wait(Until.hasObject(By.text(text).pkg(context.packageName)), 500)) {
                                visible = true
                                break
                            }
                            // Match the same exact unrelated platform crash accepted by the
                            // native Timeline oracle. A LogDate crash must remain a failure.
                            dismissNativeEmulatorPlatformDialog(device)
                        }
                        if (!visible) {
                            val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
                            instrumentation.sendStatus(0, Bundle().apply { putString("retainedDraftWindow", hierarchy) })
                            val screenshot = File(context.noBackupFilesDir, "native-retained-draft-shortcut.png")
                            if (device.takeScreenshot(screenshot)) {
                                instrumentation.sendStatus(0, Bundle().apply { putString("retainedDraftScreenshot", "saved") })
                            }
                        }
                        assertTrue(visible, "Published shortcut must open the retained editor text")
                        val screenshot = File(context.noBackupFilesDir, "native-retained-draft-shortcut.png")
                        assertTrue(device.takeScreenshot(screenshot), "Retained editor screenshot must be captured")
                        instrumentation.sendStatus(0, Bundle().apply { putString("retainedDraftScreenshot", "saved") })
                    }
                }
                val retained = drafts.getDraft(id).first().getOrThrow()
                assertEquals(
                    beforeIds,
                    drafts
                        .getDrafts()
                        .first()
                        .map { it.id }
                        .toSet(),
                )
                assertEquals(original.id, retained.id)
                assertEquals(original.createdAt, retained.createdAt)
                assertEquals(original.selectedJournalIds, retained.selectedJournalIds)
                assertEquals(original.notes.map { it.uid }, retained.notes.map { it.uid })
                assertEquals(text, (retained.notes.single { it.uid == fixtureId(22) } as JournalNote.Text).content)
                assertEquals(
                    transferredDigest,
                    digest(
                        media
                            .readMedia(
                                (
                                    retained.notes.single {
                                        it.uid == fixtureId(23)
                                    } as JournalNote.Image
                                ).mediaRef,
                            ).data,
                    ),
                )
            } finally {
                assertTrue(ShortcutManagerCompat.setDynamicShortcuts(context, previousShortcuts))
            }
        }

    private fun fixtureId(number: Int) = Uuid.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
