package app.logdate.client.e2e

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.FixedMemoryWidgetConfigActivity
import app.logdate.client.feature.widgets.FixedMemoryWidgetReceiver
import app.logdate.client.feature.widgets.NewEntryWidgetReceiver
import app.logdate.client.feature.widgets.OnThisDayWidgetReceiver
import app.logdate.client.feature.widgets.createWidgetConfigurationIntent
import app.logdate.client.feature.widgets.requireWidgetContentAccess
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            settings.saveUsePhotoPrompt(secondId, true)
            settings.savePhotoShape(firstId, PhotoWidgetShape.SOFT)
            settings.savePhotoShape(secondId, PhotoWidgetShape.CIRCLE)
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
    fun `widget content requires successful authentication when app lock is enabled`() = runBlocking {
        var prompts = 0
        assertTrue(requireWidgetContentAccess({ AppSecurityLevel.NONE }) { prompts++; false })
        assertEquals(0, prompts)
        assertEquals(false, requireWidgetContentAccess({ AppSecurityLevel.BIOMETRIC }) { prompts++; false })
        assertEquals(1, prompts)
        assertTrue(requireWidgetContentAccess({ AppSecurityLevel.BIOMETRIC }) { prompts++; true })
        assertEquals(2, prompts)
        assertEquals(false, requireWidgetContentAccess({ AppSecurityLevel.PASSWORD }) { prompts++; true })
        assertEquals(false, requireWidgetContentAccess({ null }) { prompts++; true })
        assertEquals(2, prompts)
    }

    @Test
    fun `widget settings survive launcher ID remapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = WidgetInstanceSettings(context)
        val oldId = 912301
        val newId = 912302
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
    fun `overlapping restored IDs preserve both widget choices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = WidgetInstanceSettings(context)
        val firstId = 912311
        val secondId = 912312
        val thirdId = 912313
        try {
            settings.saveChosenNote(firstId, "first-memory")
            settings.saveChosenNote(secondId, "second-memory")
            settings.remap(intArrayOf(firstId, secondId), intArrayOf(secondId, thirdId))
            assertEquals("first-memory", settings.chosenNoteId(secondId))
            assertEquals("second-memory", settings.chosenNoteId(thirdId))
            assertNull(settings.chosenNoteId(firstId))
        } finally {
            settings.remove(firstId)
            settings.remove(secondId)
            settings.remove(thirdId)
        }
    }
}
