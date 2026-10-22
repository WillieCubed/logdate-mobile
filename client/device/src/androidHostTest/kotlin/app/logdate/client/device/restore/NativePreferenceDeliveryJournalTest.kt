package app.logdate.client.device.restore

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NativePreferenceDeliveryJournalTest {
    private val directory = createTempDirectory("native-preference-delivery").toFile()
    private val file = File(directory, "delivery.json")
    private val journal = NativePreferenceDeliveryJournal(file, "delivery-one")
    private val names = setOf("logdate_widget_instances")

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `final delivery decision survives process recreation before completion publication`() {
        journal.record(names, names)

        val recreated = assertNotNull(NativePreferenceDeliveryJournal(file, "delivery-one").read())
        assertEquals("delivery-one", recreated.generation)
        assertEquals(setOf("logdate_widget_instances"), recreated.names)
        assertEquals(setOf("logdate_widget_instances"), recreated.delivered)
    }

    @Test
    fun `duplicate completion with empty callback state preserves the original delivered decision`() {
        journal.record(names, names)

        val result = NativePreferenceDeliveryJournal(file, "delivery-one").record(names, emptySet())

        assertEquals(setOf("logdate_widget_instances"), result.delivered)
        assertEquals(setOf("logdate_widget_instances"), assertNotNull(journal.read()).delivered)
    }

    @Test
    fun `a different enrollment cannot replace an accepted decision`() {
        journal.record(names, emptySet())

        assertFailsWith<IOException> { journal.record(setOf("other_preferences"), emptySet()) }

        assertEquals(setOf("logdate_widget_instances"), assertNotNull(journal.read()).names)
        assertEquals(emptySet(), assertNotNull(journal.read()).delivered)
    }

    @Test
    fun `failed persistence remains retryable without publishing a decision`() {
        val obstruction = File(directory, "delivery.json.part").apply { mkdir() }

        assertFailsWith<IOException> { journal.record(names, names) }
        assertFalse(file.exists())

        obstruction.delete()
        journal.record(names, names)
        assertEquals(setOf("logdate_widget_instances"), assertNotNull(journal.read()).delivered)
    }

    @Test
    fun `another generation cannot reuse a previous final decision`() {
        journal.record(names, names)

        assertFailsWith<IOException> { NativePreferenceDeliveryJournal(file, "delivery-two").read() }

        assertEquals("delivery-one", assertNotNull(journal.read()).generation)
    }

    @Test
    fun `malformed or oversized decisions are not replaced by an empty callback`() {
        for (contents in listOf("{truncated", "x".repeat(16_385))) {
            file.writeText(contents)
            assertFailsWith<IOException> { journal.record(names, emptySet()) }
            assertEquals(contents, file.readText())
        }
    }
}
