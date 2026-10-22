package app.logdate.client.e2e

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.feature.widgets.FixedMemoryWidgetReceiver
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real system widget registration, remapped choices and taps after duplicate native delivery. */
@RunWith(AndroidJUnit4::class)
class NativeRestoredWidgetProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val settings get() = WidgetInstanceSettings(context)
    private val manager get() = AppWidgetManager.getInstance(context)
    private val provider get() = ComponentName(context, FixedMemoryWidgetReceiver::class.java)
    private val checkpoint get() = File(context.noBackupFilesDir, "native-widget-restore.json")
    private val noteId = "00000000-0000-0000-0000-000000000003"
    private val text = "Text retained through native backup"

    @Test
    fun registeredWidgetRetainsItsEntryAcrossRestoreAndRestart() =
        runBlocking {
            val stage = arguments.getString("nativeWidgetStage")
            assumeTrue(stage != null)
            require(stage in setOf("seed", "restore", "restart", "cleanup"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            var host: AppWidgetHost? = null
            instrumentation.runOnMainSync { host = AppWidgetHost(context, HOST_ID) }
            val ownedHost = assertNotNull(host)
            if (stage == "cleanup") {
                cleanup(ownedHost)
                return@runBlocking
            }
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            val koin = GlobalContext.get()
            assertTrue(
                koin
                    .get<JournalRepository>()
                    .allJournalsObserved
                    .first()
                    .any { it.title == "Native transfer journal" },
            )
            val note = assertNotNull(koin.get<JournalNotesRepository>().getNoteById(Uuid.parse(noteId)))
            assertEquals(text, (note as JournalNote.Text).content)
            var retained = false
            try {
                val widgetId =
                    if (stage == "restart") {
                        val saved = JSONObject(checkpoint.readText())
                        saved.getInt("widgetId").also {
                            assertNotEquals(saved.getInt("oldWidgetId"), it)
                            assertNull(settings.chosenNoteId(saved.getInt("oldWidgetId")))
                            assertTrue(ownedHost.appWidgetIds.contains(it), "The widget must already exist before cold startup checks")
                            assertEquals(provider, assertNotNull(manager.getAppWidgetInfo(it)).provider)
                        }
                    } else {
                        check(!checkpoint.exists()) { "Widget acceptance requires a fresh owned host" }
                        val allocated =
                            if (stage == "restore") {
                                val sourceId = requireNotNull(arguments.getString("nativeOldWidgetId")).toInt()
                                val restored = restoredSystemWidget(ownedHost, sourceId)
                                assertNotEquals(sourceId, restored, "Native delivery must exercise a real nonidentity widget remap")
                                restored
                            } else {
                                assertTrue(ownedHost.appWidgetIds.isEmpty())
                                // Advance only the source allocator so fresh destination IDs cannot silently make remapping a no-op.
                                repeat(3) { ownedHost.deleteAppWidgetId(ownedHost.allocateAppWidgetId()) }
                                val source = ownedHost.allocateAppWidgetId()
                                bind(source)
                                settings.saveChosenNote(source, noteId)
                                settings.savePhotoShape(source, PhotoWidgetShape.CIRCLE)
                                sendUpdate(source)
                                source
                            }
                        val oldId = if (stage == "restore") requireNotNull(arguments.getString("nativeOldWidgetId")).toInt() else -1
                        JSONObject().put("widgetId", allocated).put("oldWidgetId", oldId).also {
                            checkpoint.writeText(it.toString())
                        }
                        allocated
                    }
                assertEquals(noteId, settings.chosenNoteId(widgetId))
                assertEquals(PhotoWidgetShape.CIRCLE, settings.photoShape(widgetId))
                showAndTap(ownedHost, widgetId, if (stage == "restore") "platform" else requireNotNull(stage))
                if (stage == "restore") {
                    instrumentation.sendStatus(
                        0,
                        Bundle().apply { putString("nativeWidgetPhase", "NATIVE_WIDGET_SYSTEM_RESTORED_AND_TAPPED") },
                    )
                }
                retained = true
            } finally {
                instrumentation.runOnMainSync { ownedHost.stopListening() }
                if (!retained || stage == "restart") cleanup(ownedHost)
            }
        }

    private fun bind(widgetId: Int) {
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, provider), "The synthetic host must obtain a real platform binding")
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        manager.updateAppWidgetOptions(
            widgetId,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)
            },
        )
        assertTrue(manager.getAppWidgetIds(provider).contains(widgetId))
    }

    private suspend fun restoredSystemWidget(
        host: AppWidgetHost,
        sourceId: Int,
    ): Int {
        try {
            return withTimeout(30_000) {
                while (true) {
                    val ids = host.appWidgetIds
                    check(ids.size <= 1) { "Native transfer must restore only the single synthetic widget" }
                    val restored = ids.singleOrNull()
                    if (restored != null &&
                        manager.getAppWidgetInfo(restored)?.provider == provider &&
                        settings.chosenNoteId(restored) == noteId
                    ) {
                        assertEquals(PhotoWidgetShape.CIRCLE, settings.photoShape(restored))
                        if (sourceId != restored) assertNull(settings.chosenNoteId(sourceId))
                        return@withTimeout restored
                    }
                    delay(50)
                }
                @Suppress("UNREACHABLE_CODE")
                error("The platform did not restore the registered widget and its choice")
            }
        } catch (failure: Exception) {
            val ids = host.appWidgetIds
            val snapshot =
                JSONObject()
                    .put("registeredIds", ids.toList())
                    .put("sourceId", sourceId)
                    .put("sourceChoice", settings.chosenNoteId(sourceId))
                    .put("sourceShape", settings.photoShape(sourceId).name)
                    .put("recoveryPending", settings.isRecoveryPending)
            val recovery = File(context.noBackupFilesDir, "widget-restore-choices.json")
            snapshot.put("recoveryFilePresent", recovery.exists())
            if (recovery.isFile && recovery.length() < 65536) {
                snapshot.put("syntheticRecoveryState", recovery.readText())
            }
            ids.forEach { id ->
                snapshot.put("$id.provider", manager.getAppWidgetInfo(id)?.provider?.flattenToString())
                snapshot.put("$id.choice", settings.chosenNoteId(id))
                snapshot.put("$id.shape", settings.photoShape(id).name)
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("nativeWidgetRestoreFailure", snapshot.toString()) })
            throw failure
        }
    }

    private fun sendUpdate(widgetId: Int) {
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId)),
        )
    }

    private fun showAndTap(
        host: AppWidgetHost,
        widgetId: Int,
        stage: String,
    ) {
        try {
            renderAndTap(host, widgetId, stage)
        } catch (failure: Throwable) {
            runCatching {
                val device = UiDevice.getInstance(instrumentation)
                check(device.takeScreenshot(File(context.noBackupFilesDir, "native-widget-failure.png")))
                device.dumpWindowHierarchy(File(context.noBackupFilesDir, "native-widget-failure.xml"))
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun renderAndTap(
        host: AppWidgetHost,
        widgetId: Int,
        stage: String,
    ) {
        val device = UiDevice.getInstance(instrumentation)
        dismissNativeEmulatorPlatformDialog(device)
        ActivityScenario.launch(ShareEntryPointHostActivity::class.java).use { scenario ->
            var view: AppWidgetHostView? = null
            scenario.onActivity { activity ->
                host.startListening()
                val density = activity.resources.displayMetrics.density
                val container = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
                val widget = host.createView(activity, widgetId, assertNotNull(manager.getAppWidgetInfo(widgetId)))
                container.addView(
                    widget,
                    FrameLayout.LayoutParams((320 * density).roundToInt(), (200 * density).roundToInt(), Gravity.CENTER),
                )
                activity.addContentView(
                    container,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                view = widget
            }
            val deadline = SystemClock.uptimeMillis() + 45_000
            var rendered = false
            while (!rendered && SystemClock.uptimeMillis() < deadline) {
                dismissNativeEmulatorPlatformDialog(device)
                scenario.onActivity { rendered = assertNotNull(view).hasRetainedText() }
                rendered = rendered && device.hasObject(By.textContains(text).pkg(context.packageName))
                if (!rendered) SystemClock.sleep(50)
            }
            assertTrue(rendered, "Production widget refresh must render the retained entry inside the real host view")
            assertTrue(device.takeScreenshot(File(context.noBackupFilesDir, "native-widget-$stage.png")))
            val point = IntArray(2)
            scenario.onActivity {
                val widget = assertNotNull(view)
                widget.getLocationOnScreen(point)
                point[0] += widget.width / 2
                point[1] += widget.height / 2
            }
            dismissNativeEmulatorPlatformDialog(device)
            assertTrue(device.click(point[0], point[1]))
            val viewerDeadline = SystemClock.uptimeMillis() + 30_000
            var opened = false
            while (!opened && SystemClock.uptimeMillis() < viewerDeadline) {
                dismissNativeEmulatorPlatformDialog(device)
                opened = device.wait(Until.hasObject(By.desc("Add to journal").pkg(context.packageName)), 500)
            }
            assertTrue(
                opened,
                "Widget tap must open the note viewer",
            )
            assertTrue(device.wait(Until.hasObject(By.desc("Share").pkg(context.packageName)), 5_000))
            assertTrue(
                device.wait(Until.hasObject(By.text(text).pkg(context.packageName)), 5_000),
                "The opened entry must be the chosen note",
            )
            assertTrue(device.takeScreenshot(File(context.noBackupFilesDir, "native-widget-$stage-detail.png")))
            device.pressBack()
            assertTrue(
                device.wait(Until.gone(By.desc("Add to journal").pkg(context.packageName)), 5_000),
                "The next tap must start outside the note viewer",
            )
        }
    }

    private fun View.hasRetainedText(): Boolean =
        (this is TextView && this.text.toString().contains(text)) ||
            (this is ViewGroup && (0 until childCount).any { getChildAt(it).hasRetainedText() })

    private fun cleanup(host: AppWidgetHost) {
        host.appWidgetIds.forEach { widgetId ->
            host.deleteAppWidgetId(widgetId)
            settings.remove(widgetId)
        }
        checkpoint.delete()
        assertTrue(host.appWidgetIds.isEmpty())
    }

    private companion object {
        const val HOST_ID = 913_405
    }
}
