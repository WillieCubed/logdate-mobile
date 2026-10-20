package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerateAndroidShortcutsTest {
    private val template =
        """
        <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
            <shortcut android:shortcutId="new_note">
                <intent android:targetPackage="@APPLICATION_ID@" android:targetClass="app.logdate.client.MainActivity" />
            </shortcut>
            <shortcut android:shortcutId="record_voice">
                <intent android:targetPackage="@APPLICATION_ID@" android:targetClass="app.logdate.client.MainActivity" />
            </shortcut>
            <shortcut android:shortcutId="take_photo">
                <intent android:targetPackage="@APPLICATION_ID@" android:targetClass="app.logdate.client.MainActivity" />
            </shortcut>
        </shortcuts>
        """.trimIndent()

    @Test
    fun `every target uses the exact variant application id`() {
        for (applicationId in listOf("studio.hypertext.logdate", "studio.hypertext.logdate.debug", "co.reasonabletech.logdate")) {
            val xml = androidShortcutXml(template, applicationId)
            val targets = Regex("android:targetPackage=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
            assertEquals(List(3) { applicationId }, targets)
        }
    }

    @Test
    fun `package names cannot inject resource references or XML`() {
        for (invalid in listOf("@string/application_id", "studio.example\" android:enabled=\"false", "")) {
            assertFailsWith<IllegalArgumentException> { androidShortcutXml(template, invalid) }
        }
    }

    @Test
    fun `templates must declare package substitution`() {
        assertFailsWith<IllegalArgumentException> {
            androidShortcutXml(template.replace("@APPLICATION_ID@", "@string/logdate_application_id"), "studio.hypertext.logdate")
        }
    }
}
