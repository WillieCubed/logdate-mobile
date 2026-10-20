package app.logdate.client.e2e

import android.content.pm.ShortcutManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Reads the platform-parsed shortcuts without launching capture or changing journal data. */
@RunWith(AndroidJUnit4::class)
class RestoredShortcutTargetsRuntimeTest {
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
