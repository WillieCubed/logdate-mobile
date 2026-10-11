package app.logdate.wear.v2.playon

import android.provider.Settings
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertTrue

/**
 * Package visibility hides both pickers from the app unless its manifest names their intents, and
 * the emulator's instrumentation sees them regardless, so only this check notices a missing entry.
 */
class OutputPickerManifestTest {
    private val queriedActions = queriedActions(File("src/main/AndroidManifest.xml"))

    @Test
    fun `the manifest lets the app look up the output switcher`() {
        assertTrue(SystemOutputPicker.ACTION_MEDIA_OUTPUT in queriedActions, "queried $queriedActions")
    }

    @Test
    fun `the manifest lets the app look up Bluetooth settings`() {
        assertTrue(Settings.ACTION_BLUETOOTH_SETTINGS in queriedActions, "queried $queriedActions")
    }

    private fun queriedActions(manifest: File): Set<String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
        val queries = document.getElementsByTagName("queries")
        if (queries.length == 0) return emptySet()
        val actions = (queries.item(0) as Element).getElementsByTagName("action")
        return (0 until actions.length).map { (actions.item(it) as Element).getAttributeNS(ANDROID_NAMESPACE, "name") }.toSet()
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
