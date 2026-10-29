package app.logdate.ui.creation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryCreationLauncherTest {
    @Test
    fun nestedAndThrowingLaunchesRestoreSynchronousMetadata() {
        val launcher = EntryCreationLauncher()
        assertNull(launcher.currentSourceId)
        launcher.withSource("action") {
            assertEquals("action", launcher.currentSourceId)
            assertFailsWith<IllegalStateException> {
                launcher.withSource("pull") {
                    assertEquals("pull", launcher.currentSourceId)
                    error("Navigation rejected")
                }
            }
            assertEquals("action", launcher.currentSourceId)
        }
        assertNull(launcher.currentSourceId)
    }

    @Test
    fun committedSourceRejectsDoubleAndStaleCallbacksUntilACompletedReturn() {
        val source = EntryCreationSource(initialId = "visit-one", newId = { "visit-two" })
        val launcher = EntryCreationLauncher()
        val routes = mutableListOf<String?>()
        val oldId = source.id
        assertTrue(source.launch(oldId, launcher) { routes += launcher.currentSourceId })
        assertFalse(source.launch(oldId, launcher) { routes += launcher.currentSourceId })
        source.observeVisibility(visible = false, settled = false)
        source.observeVisibility(visible = true, settled = false)
        assertEquals(oldId, source.id, "Predictive return must keep the actual matching source")
        source.observeVisibility(visible = false, settled = false)
        assertEquals(oldId, source.id, "Cancelled return must not rotate the source")
        source.observeVisibility(visible = true, settled = true)
        assertEquals("visit-two", source.id)
        assertFalse(source.launch(oldId, launcher) { routes += launcher.currentSourceId })
        assertTrue(source.launch(source.id, launcher) { routes += launcher.currentSourceId })
        assertEquals(listOf<String?>("visit-one", "visit-two"), routes)
    }

    @Test
    fun independentActionAndPullSourcesCannotShareAKey() {
        val action = EntryCreationSource(initialId = "action")
        val pull = EntryCreationSource(initialId = "pull")
        assertNotEquals(entryCreationKey(action.id), entryCreationKey(pull.id))
    }

    @Test
    fun disposalRejectsCallbacksAndFailedNavigationCanBeRetried() {
        val source = EntryCreationSource(initialId = "action")
        val launcher = EntryCreationLauncher()
        assertFailsWith<IllegalStateException> { source.launch(source.id, launcher) { error("Navigation failed") } }
        assertNull(launcher.currentSourceId)
        var visits = 0
        assertTrue(source.launch(source.id, launcher) { visits++ })
        source.dispose()
        assertFalse(source.launch(source.id, launcher) { visits++ })
        assertEquals(1, visits)
    }
}
