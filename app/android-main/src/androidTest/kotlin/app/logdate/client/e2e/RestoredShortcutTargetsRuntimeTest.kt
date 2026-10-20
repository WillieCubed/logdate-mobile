package app.logdate.client.e2e

import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutDescriptor
import app.logdate.client.shortcuts.AndroidDynamicShortcutApplier
import coil3.ImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Reads the platform-parsed shortcuts without launching capture or changing journal data. */
@RunWith(AndroidJUnit4::class)
class RestoredShortcutTargetsRuntimeTest {
    @Test
    fun recreatedDraftShortcutTargetsTheRetainedDraftWithoutDuplicates() =
        runBlocking<Unit> {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assumeTrue(InstrumentationRegistry.getArguments().getString("nativeShortcutAcceptance") == "true")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val context = instrumentation.targetContext
            require(context.packageName.endsWith(".debug"))
            val original = ShortcutManagerCompat.getDynamicShortcuts(context)
            val imageLoader = ImageLoader.Builder(context).build()
            try {
                val draft = DynamicShortcutDescriptor.ContinueDraft(Uuid.random())
                val applier = AndroidDynamicShortcutApplier(context, imageLoader)
                repeat(2) { assertTrue(applier.apply(listOf(draft)), "Android must accept reconstructed shortcuts") }
                val shortcut = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id == draft.id }
                val intent = assertNotNull(shortcut.intent)
                assertEquals(context.packageName, assertNotNull(intent.component).packageName)
                assertEquals(AMBIENT_PROMPT_TARGET_DRAFT, intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET))
                assertEquals(draft.draftId.toString(), intent.getStringExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID))
                assertNotNull(context.packageManager.resolveActivity(intent, 0))
            } finally {
                restoreFixtureState(
                    { assertTrue(ShortcutManagerCompat.setDynamicShortcuts(context, original), "Restore original shortcut state") },
                    { imageLoader.shutdown() },
                )
            }
        }

    @Test
    fun manifestCaptureShortcutsTargetInstalledApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeShortcutAcceptance") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = instrumentation.targetContext
        require(context.packageName.endsWith(".debug"))
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        assertEquals(setOf("new_note", "record_voice", "take_photo"), shortcuts.map { it.id }.toSet())
        shortcuts.forEach { shortcut ->
            val intent = assertNotNull(shortcut.intent)
            val component = assertNotNull(intent.component)
            assertEquals(context.packageName, component.packageName, "${shortcut.id} must target the installed application")
            assertNotNull(context.packageManager.resolveActivity(intent, 0), "${shortcut.id} must resolve after restore")
        }
    }
}
