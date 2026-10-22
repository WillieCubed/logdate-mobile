package app.logdate.client.e2e

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.feature.widgets.FixedMemoryWidgetConfigActivity
import app.logdate.client.feature.widgets.FixedMemoryWidgetReceiver
import app.logdate.client.feature.widgets.NewEntryWidgetReceiver
import app.logdate.client.feature.widgets.OnThisDayWidgetReceiver
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import app.logdate.client.feature.widgets.createWidgetConfigurationIntent
import app.logdate.client.feature.widgets.requireWidgetContentAccess
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class WidgetInstanceSettingsTest {
    @Test
    fun `launcher exposes three widgets and shape setup on every supported version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider.className }
        assertTrue(providers.containsKey(FixedMemoryWidgetReceiver::class.java.name))
        assertTrue(providers.containsKey(OnThisDayWidgetReceiver::class.java.name))
        val newEntry = providers[NewEntryWidgetReceiver::class.java.name] ?: error("New Entry widget is missing")
        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(0, newEntry.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)
            assertTrue(newEntry.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
        }
        assertEquals("app.logdate.client.feature.widgets.NewEntryWidgetConfigActivity", newEntry.configure?.className)
    }

    @Test
    fun `unconfigured pinned memory opens its own setup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = createWidgetConfigurationIntent(context, 911203)
        assertEquals(FixedMemoryWidgetConfigActivity::class.java.name, intent.component?.className)
        assertEquals(911203, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `each widget keeps independent choices and removal preserves other widgets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = WidgetInstanceSettings(context)
        val firstId = 911201
        val secondId = 911202
        try {
            settings.saveRecall(firstId, RecallMode.ON_THIS_DAY, setOf(WidgetContentType.PHOTOS))
            settings.saveRecall(secondId, RecallMode.REDISCOVER, setOf(WidgetContentType.TEXT))
            settings.ensureRecallDefaults(secondId, RecallMode.ON_THIS_DAY, WidgetContentType.ALL)
            settings.saveChosenNote(firstId, "chosen-entry")
            assertEquals(true, settings.usePhotoPrompt(firstId))
            assertEquals(false, settings.hasPhotoPromptChoice(firstId))
            settings.savePhotoShape(firstId, PhotoWidgetShape.SOFT)
            assertTrue(settings.savePhotoPrompt(secondId, enabled = true, shape = PhotoWidgetShape.CIRCLE))
            assertEquals(true, settings.hasPhotoPromptChoice(secondId))
            assertEquals(RecallMode.ON_THIS_DAY, settings.recallMode(firstId, RecallMode.REDISCOVER))
            assertEquals(RecallMode.REDISCOVER, settings.recallMode(secondId, RecallMode.ON_THIS_DAY))
            assertEquals(setOf(WidgetContentType.PHOTOS), settings.contentTypes(firstId, WidgetContentType.ALL))
            assertEquals(setOf(WidgetContentType.TEXT), settings.contentTypes(secondId, WidgetContentType.ALL))
            assertEquals(true, settings.usePhotoPrompt(firstId))
            assertEquals(true, settings.usePhotoPrompt(secondId))
            assertEquals(PhotoWidgetShape.SOFT, settings.photoShape(firstId))
            assertEquals(PhotoWidgetShape.CIRCLE, settings.photoShape(secondId))
            settings.remove(firstId)
            assertNull(settings.chosenNoteId(firstId))
            assertEquals(PhotoWidgetShape.SYSTEM, settings.photoShape(firstId))
            assertEquals(PhotoWidgetShape.CIRCLE, settings.photoShape(secondId))
            assertEquals(RecallMode.REDISCOVER, settings.recallMode(secondId, RecallMode.ON_THIS_DAY))
            assertEquals(true, settings.usePhotoPrompt(secondId))
        } finally {
            settings.remove(firstId)
            settings.remove(secondId)
        }
    }

    @Test
    fun `widget content requires successful authentication when app lock is enabled`() =
        runBlocking {
            var prompts = 0
            assertTrue(
                requireWidgetContentAccess({ AppSecurityLevel.NONE }) {
                    prompts++
                    false
                },
            )
            assertEquals(0, prompts)
            assertEquals(
                false,
                requireWidgetContentAccess({ AppSecurityLevel.BIOMETRIC }) {
                    prompts++
                    false
                },
            )
            assertEquals(1, prompts)
            assertTrue(
                requireWidgetContentAccess({ AppSecurityLevel.BIOMETRIC }) {
                    prompts++
                    true
                },
            )
            assertEquals(2, prompts)
            assertEquals(
                false,
                requireWidgetContentAccess({ AppSecurityLevel.PASSWORD }) {
                    prompts++
                    true
                },
            )
            assertEquals(
                false,
                requireWidgetContentAccess({ null }) {
                    prompts++
                    true
                },
            )
            assertEquals(2, prompts)
        }

    @Test
    fun `widget settings survive launcher ID remapping`() =
        withBoundWidgetIds(1) { widgetIds ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val settings = WidgetInstanceSettings(context)
            val oldId = 912301
            val newId = widgetIds.single()
            try {
                settings.saveRecall(oldId, RecallMode.REDISCOVER, setOf(WidgetContentType.AUDIO))
                settings.saveChosenNote(oldId, "chosen-memory")
                settings.saveUsePhotoPrompt(oldId, false)
                settings.savePhotoShape(oldId, PhotoWidgetShape.CIRCLE)
                settings.remap(oldId, newId)
                assertEquals(RecallMode.REDISCOVER, settings.recallMode(newId, RecallMode.ON_THIS_DAY))
                assertEquals(setOf(WidgetContentType.AUDIO), settings.contentTypes(newId, WidgetContentType.ALL))
                assertEquals("chosen-memory", settings.chosenNoteId(newId))
                assertEquals(false, settings.usePhotoPrompt(newId))
                assertEquals(PhotoWidgetShape.CIRCLE, settings.photoShape(newId))
                assertNull(settings.chosenNoteId(oldId))
            } finally {
                settings.remove(oldId)
                settings.remove(newId)
            }
        }

    @Test
    fun `overlapping restored IDs preserve both widget choices`() =
        withBoundWidgetIds(2) { widgetIds ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val root = File(context.cacheDir, "widget-overlap-${Uuid.random()}")
            check(root.mkdir())
            val fixture = isolatedSettingsContext(context, root)
            val preferences = fixture.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)
            val firstId = 912311
            val secondId = widgetIds[0]
            val thirdId = widgetIds[1]
            try {
                check(
                    preferences
                        .edit()
                        .putString("$firstId.chosenNoteId", "first-memory")
                        .putString("$secondId.chosenNoteId", "second-memory")
                        .commit(),
                )
                repeat(2) {
                    val settings = WidgetInstanceSettings(fixture)
                    settings.remap(intArrayOf(firstId, secondId), intArrayOf(secondId, thirdId))
                    assertEquals("first-memory", settings.chosenNoteId(secondId))
                    assertEquals("second-memory", settings.chosenNoteId(thirdId))
                    assertNull(settings.chosenNoteId(firstId))
                }
            } finally {
                check(preferences.edit().clear().commit())
                check(root.deleteRecursively())
            }
        }

    @Test
    fun `restored source ID collision preserves an independent live widget after restart`() =
        withBoundWidgetIds(3) { widgetIds ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val root = File(context.cacheDir, "widget-live-collision-${Uuid.random()}")
            check(root.mkdir())
            val fixture = isolatedSettingsContext(context, root)
            val preferences = fixture.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)
            val localId = widgetIds[0]
            val restoredId = widgetIds[1]
            val replayId = widgetIds[2]
            try {
                val settings = WidgetInstanceSettings(fixture)
                assertTrue(settings.saveChosenNote(localId, "independent-local-memory"))
                check(preferences.edit().putString("$localId.chosenNoteId", "restored-memory").commit())
                settings.remap(localId, restoredId)
                assertEquals("independent-local-memory", settings.chosenNoteId(localId))
                assertEquals("restored-memory", settings.chosenNoteId(restoredId))
                val restarted = WidgetInstanceSettings(fixture)
                restarted.remap(localId, restoredId)
                assertEquals("independent-local-memory", restarted.chosenNoteId(localId))
                assertEquals("restored-memory", restarted.chosenNoteId(restoredId))
                restarted.remove(restoredId)
                assertTrue(restarted.saveChosenNote(restoredId, "new-explicit-memory"))
                check(preferences.edit().putString("$localId.chosenNoteId", "restored-memory").commit())
                val reused = WidgetInstanceSettings(fixture)
                reused.remap(localId, replayId)
                assertEquals("independent-local-memory", reused.chosenNoteId(localId))
                assertEquals("new-explicit-memory", reused.chosenNoteId(restoredId))
                assertNull(reused.chosenNoteId(replayId))
                assertFalse(reused.isRecoveryPending)
                assertFalse(restarted.isRecoveryPending)
            } finally {
                check(preferences.edit().clear().commit())
                check(root.deleteRecursively())
            }
        }

    @Test
    fun `unwritable recovery storage retains the current choice and permits a later save`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "widget-storage-${Uuid.random()}")
        check(root.createNewFile())
        val fixture = isolatedSettingsContext(context, root)
        val preferences = fixture.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)
        check(preferences.edit().putString("911209.chosenNoteId", "retained-choice").commit())
        try {
            val settings = WidgetInstanceSettings(fixture)
            assertFalse(settings.saveChosenNote(911209, "new-choice"))
            assertTrue(settings.isRecoveryPending)
            assertEquals("retained-choice", settings.chosenNoteId(911209))
            check(root.delete() && root.mkdir())
            assertTrue(settings.saveChosenNote(911209, "new-choice"))
            assertFalse(settings.isRecoveryPending)
            assertEquals("new-choice", settings.chosenNoteId(911209))
        } finally {
            check(preferences.edit().clear().commit())
            check(root.deleteRecursively())
        }
    }

    @Test
    fun `damaged widget metadata preserves its bytes and rebuilds registered choices`() =
        withBoundWidgetIds(1) { ids ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val root = File(context.cacheDir, "widget-metadata-${Uuid.random()}")
            check(root.mkdir())
            val fixture = isolatedSettingsContext(context, root)
            val preferences = fixture.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)
            try {
                assertTrue(WidgetInstanceSettings(fixture).saveChosenNote(ids.single(), "retained-choice"))
                val metadata = File(root, "widget-restore-choices.json")
                metadata.writeText("{interrupted metadata")
                val recovered = WidgetInstanceSettings(fixture)
                assertFalse(recovered.isRecoveryPending)
                assertEquals("retained-choice", recovered.chosenNoteId(ids.single()))
                val damaged = root.listFiles().orEmpty().single { it.name.contains(".damaged-") }
                assertEquals("{interrupted metadata", damaged.readText())
                assertTrue(recovered.saveChosenNote(ids.single(), "new-choice"))
            } finally {
                check(preferences.edit().clear().commit())
                check(root.deleteRecursively())
            }
        }

    private fun isolatedSettingsContext(
        context: Context,
        storage: File,
    ): Context {
        val prefix = "widget-fixture-${Uuid.random()}-"
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getNoBackupFilesDir(): File = storage

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ) = context.getSharedPreferences(prefix + name, mode)
        }
    }

    private fun withBoundWidgetIds(
        count: Int,
        verify: (List<Int>) -> Unit,
    ) {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val host = AppWidgetHost(context, 913_409)
        val manager = AppWidgetManager.getInstance(context)
        val ids = mutableListOf<Int>()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            repeat(count) {
                val id = host.allocateAppWidgetId()
                ids += id
                assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, FixedMemoryWidgetReceiver::class.java)))
            }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            verify(ids)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            ids.forEach { id ->
                host.deleteAppWidgetId(id)
                WidgetInstanceSettings(context).remove(id)
            }
        }
    }
}
