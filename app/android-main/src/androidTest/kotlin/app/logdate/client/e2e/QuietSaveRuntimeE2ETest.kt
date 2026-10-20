package app.logdate.client.e2e

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.EditorActivity
import app.logdate.client.MainActivity
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Exercises native microphone capture and durable saving; level-policy fixtures run separately. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class QuietSaveRuntimeE2ETest {
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO,
        )

    @Test
    fun nativeQuietRecordingIsSavedWhileLockedAndOpensFromItsNotification() =
        runBlocking<Unit> {
            check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Quiet Save acceptance requires an emulator" }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val scenario = ActivityScenario.launch(EditorActivity::class.java)
            try {
                val koin = GlobalContext.get()
                val flags = koin.get<FeatureFlagStore>()
                flags.setEnabled(FeatureFlag.QUIET_SAVE, true)
                val optOut = InstrumentationRegistry.getArguments().getString("logdate.quietSaveOptOut") == "true"
                val expectNotification = InstrumentationRegistry.getArguments().getString("logdate.quietSaveNotification") != "false"
                assertEquals(
                    expectNotification,
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED,
                )
                koin.get<LogdatePreferencesDataSource>().setQuietSaveEnabled(!optOut)
                koin.get<LogdatePreferencesDataSource>().setShouldHideOnboarding(true)
                val repository = koin.get<EntryDraftRepository>()
                repository
                    .getDrafts()
                    .first()
                    .filter { it.quietSave != null }
                    .forEach { repository.acknowledgeQuietSave(it.id) }
                val recorder = koin.get<AudioRecordingManager>()
                val blockId = Uuid.random()
                val now = Clock.System.now()
                val content = "Quiet Save acceptance ${blockId.toString().take(8)}"
                val text = JournalNote.Text(content = content, creationTimestamp = now, lastUpdated = now)
                val draftId =
                    repository.createDraft(
                        Uuid.random(),
                        listOf(text),
                        listOf(
                            PendingMediaRecord(
                                blockId,
                                PendingMediaType.AUDIO,
                                now,
                                timeZoneId = TimeZone.currentSystemDefault().id,
                                position = 1,
                            ),
                        ),
                    )
                assertTrue(recorder.startRecording(blockId))
                InstrumentationRegistry.getInstrumentation().sendStatus(
                    2,
                    Bundle().apply { putString("stream", "QUIET_SAVE_RECORDING_STARTED\n") },
                )
                val active =
                    repository
                        .getDraft(draftId)
                        .first()
                        .getOrThrow()
                        .pendingMedia
                        .single()
                assertNotNull(active.filePath)
                if (!optOut) {
                    val draftIntent =
                        Intent(context, EditorActivity::class.java)
                            .putExtra("app.logdate.extra.HANDOFF_DRAFT_ID", draftId.toString())
                            .setData(Uri.parse("logdate://quiet-save-active/$draftId"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val draftEditor = ActivityScenario.launch<EditorActivity>(draftIntent)
                    val editor = CompletableDeferred<EntryEditorViewModel>()
                    draftEditor.onActivity { editor.complete(ViewModelProvider(it)[EntryEditorViewModel::class.java]) }
                    val loaded =
                        withTimeoutOrNull(15_000) {
                            editor.await().editorState.first { state ->
                                state.draftState == DraftState.Active(draftId) &&
                                    state.blocks.filterIsInstance<TextBlockUiState>().any { it.content == content }
                            }
                        } != null
                    if (!loaded) {
                        device.takeScreenshot(File(context.getExternalFilesDir(null), "quiet-save-active-editor-failure.png"))
                        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "quiet-save-active-editor-failure.xml"))
                    }
                    assertTrue("The active draft must open before testing editor disposal", loaded)
                    draftEditor.close()
                    scenario.close()
                    delay(500)
                    assertTrue("Closing the editor must leave Quiet Save recording", recorder.isRecording)
                }
                device.pressHome()
                device.sleep()
                val minimumDuration =
                    InstrumentationRegistry
                        .getArguments()
                        .getString(
                            "logdate.quietSaveMinimumDurationMs",
                        )?.toLongOrNull()
                        ?.coerceAtLeast(119_000L)
                        ?: 119_000L
                if (optOut) {
                    delay(125_000)
                    assertTrue("Opt-out must keep recording after two minutes of quiet", recorder.isRecording)
                    assertNotNull(recorder.stopRecording())
                    val manualDraft = repository.getDraft(draftId).first().getOrThrow()
                    assertTrue(manualDraft.quietSave == null)
                    assertEquals(1, manualDraft.notes.filterIsInstance<JournalNote.Audio>().size)
                    assertTrue(manualDraft.pendingMedia.isEmpty())
                    return@runBlocking
                }
                val saved =
                    withTimeout(minimumDuration + 60_000) {
                        repository.getDraft(draftId).first { it.getOrNull()?.quietSave != null }.getOrThrow()
                    }
                assertFalse(recorder.isRecording)
                val retainedText = saved.notes.first() as JournalNote.Text
                assertEquals(text.copy(lastUpdated = retainedText.lastUpdated), retainedText)
                assertTrue(saved.pendingMedia.isEmpty())
                val audio = saved.notes.filterIsInstance<JournalNote.Audio>().single()
                assertEquals(blockId, audio.uid)
                assertTrue(File(audio.mediaRef).length() > 0)
                assertTrue((koin.get<AudioDurationResolver>().resolveDurationMs(audio.mediaRef) ?: 0) >= minimumDuration)
                if (!expectNotification) {
                    assertTrue(
                        context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                            it.tag ==
                                draftId.toString()
                        },
                    )
                    assertFalse(saved.quietSave!!.acknowledged)
                    return@runBlocking
                }
                val notification =
                    withTimeout(5_000) {
                        val notifications = context.getSystemService(NotificationManager::class.java)
                        while (notifications.activeNotifications.none { it.tag == draftId.toString() }) delay(100)
                        notifications.activeNotifications.first { it.tag == draftId.toString() }
                    }
                assertNotNull(notification)
                assertEquals("Quiet Save", notification!!.notification.extras.getString("android.title"))
                device.wakeUp()
                device.openNotification()
                val target = device.wait(Until.findObject(By.text("Quiet Save")), 10_000)
                assertNotNull(target)
                device.waitForIdle()
                device.takeScreenshot(File(context.getExternalFilesDir(null), "quiet-save-notification.png"))
                target!!.click()
                assertTrue(device.wait(Until.hasObject(By.text(content)), 15_000))
                device.waitForIdle()
                device.takeScreenshot(File(context.getExternalFilesDir(null), "quiet-save-draft.png"))
                device.pressBack()
                val returnedHome =
                    ActivityScenario.launch<MainActivity>(
                        context.packageManager.getLaunchIntentForPackage(context.packageName)!!,
                    )
                val acknowledgement =
                    device.wait(
                        Until.findObject(By.text("Quiet Save saved your recording as a draft after 2 minutes of quiet.")),
                        15_000,
                    )
                assertNotNull("Home must acknowledge the saved recording", acknowledgement)
                device.takeScreenshot(File(context.getExternalFilesDir(null), "quiet-save-acknowledgement.png"))
                device.findObject(By.text("Open draft")).click()
                assertTrue(device.wait(Until.hasObject(By.text(content)), 15_000))
                assertTrue(
                    repository
                        .getDraft(draftId)
                        .first()
                        .getOrThrow()
                        .quietSave!!
                        .acknowledged,
                )
                device.pressBack()
                assertFalse(
                    device.wait(Until.hasObject(By.text("Quiet Save saved your recording as a draft after 2 minutes of quiet.")), 2_000),
                )
                File(audio.mediaRef).copyTo(File(context.getExternalFilesDir(null), "quiet-save-recording.m4a"), overwrite = true)
                returnedHome.close()
            } finally {
                device.wakeUp()
                scenario.close()
            }
        }
}
