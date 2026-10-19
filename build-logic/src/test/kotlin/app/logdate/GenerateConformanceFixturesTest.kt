package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateConformanceFixturesTest {
    @Test
    fun `characters that mean something to Kotlin are escaped`() {
        assertEquals("\"a\\\\b\\\"c\\\$type\\n\\t\"", kotlinString("a\\b\"c\$type\n\t"))
    }

    @Test
    fun `characters outside printable ASCII become unicode escapes`() {
        assertEquals("\"caf\\u00e9\\u0001\"", kotlinString("café\u0001"))
    }

    @Test
    fun `the source holds every file under its name`() {
        val source =
            conformanceFixtureSource(
                packageName = "app.logdate.space",
                objectName = "SealedConformanceFixtures",
                files = mapOf("padding.json" to "{\"a\": 1}", "key-schedule.json" to "{}"),
            )

        assertTrue(source.contains("package app.logdate.space\n"))
        assertTrue(source.contains("internal object SealedConformanceFixtures {"))
        assertTrue(source.contains("\"padding.json\" to \"{\\\"a\\\": 1}\","))
        assertTrue(source.contains("\"key-schedule.json\" to \"{}\","))
    }
}
