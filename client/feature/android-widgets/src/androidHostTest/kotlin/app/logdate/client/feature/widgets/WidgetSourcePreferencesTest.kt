package app.logdate.client.feature.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class WidgetSourcePreferencesTest {
    @Test
    fun `restored choices preserve escaped strings flags and per-instance identity`() {
        val parsed =
            parseWidgetSourcePreferences(
                """<?xml version="1.0" encoding="utf-8"?><map>
                <string name="5.chosenNoteId">entry&amp;id</string>
                <string name="5.photoShape">CIRCLE</string>
                <boolean name="2.usePhotoPrompt" value="false" />
                <string name="unrelated">ignored</string>
                </map>""".encodeToByteArray(),
            )
        assertEquals(
            mapOf(
                5 to WidgetSettingValues(strings = mapOf("chosenNoteId" to "entry&id", "photoShape" to "CIRCLE")),
                2 to WidgetSettingValues(flags = mapOf("usePhotoPrompt" to false)),
            ),
            parsed,
        )
    }

    @Test
    fun `malformed duplicate or unsupported choice types cannot be interpreted as valid choices`() {
        for (source in listOf(
            "<map><string name=\"5.photoShape\">unfinished",
            "<map><string name=\"5.photoShape\">CIRCLE</string><string name=\"5.photoShape\">SOFT</string></map>",
            "<map><int name=\"5.photoShape\" value=\"1\"/></map>",
            "<map><boolean name=\"5.usePhotoPrompt\" value=\"invalid\"/></map>",
            "<other><string name=\"5.photoShape\">CIRCLE</string></other>",
        )) {
            assertFails(source) { parseWidgetSourcePreferences(source.encodeToByteArray()) }
        }
    }

    @Test
    fun `document types and entity expansion are rejected before reading any external input`() {
        for (source in listOf(
            """<!DOCTYPE map [<!ENTITY a "expanded">]><map><string name="5.chosenNoteId">&a;</string></map>""",
            """<!DOCTYPE map SYSTEM "file:///must-not-read"><map/>""",
        )) {
            assertFails { parseWidgetSourcePreferences(source.encodeToByteArray()) }
        }
        assertFails { parseWidgetSourcePreferences(ByteArray(1_048_577)) }
    }
}
