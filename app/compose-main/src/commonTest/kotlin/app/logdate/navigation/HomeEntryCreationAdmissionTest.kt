package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.ui.creation.EntryCreationLauncher
import app.logdate.ui.creation.EntryCreationShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeEntryCreationAdmissionTest {
    @Test
    fun productionCallbackCopiesTheActualSourceShapeAndRejectsACompetingSource() {
        val stack = mutableListOf<NavKey>(HomeRoute)
        val launcher = EntryCreationLauncher()
        val create = stack.homeEntryCreation(launcher)
        launcher.withSource("compact", EntryCreationShape.Round, create)
        launcher.withSource("pull", EntryCreationShape.Large, create)
        assertEquals(2, stack.size)
        val editor = stack.last() as EntryEditorRoute
        assertEquals("compact", editor.creationTransitionId)
        assertEquals(EntryCreationShape.Round, editor.creationTransitionShape)
        assertNull(launcher.currentSourceId)
    }

    @Test
    fun actionAndPullCallbacksInTheSameFrameAdmitOnlyTheFirstEditor() {
        for ((first, second) in listOf("pull" to "action", "action" to "pull")) {
            val stack = mutableListOf<NavKey>(HomeRoute)
            val launcher = EntryCreationLauncher()
            val createFromHome = { stack.tryLaunchHomeEditor(launcher.currentSourceId) }

            // No composition/frame boundary: the second source still has an idle local lease.
            assertTrue(launcher.withSource(first, createFromHome))
            assertFalse(launcher.withSource(second, createFromHome))
            assertEquals(2, stack.size)
            assertEquals(first, (stack.last() as EntryEditorRoute).creationTransitionId)
            assertNull(launcher.currentSourceId)

            stack.removeAt(stack.lastIndex)
            assertTrue(launcher.withSource("next-$first", createFromHome))
            assertEquals(2, stack.size)
            assertEquals("next-$first", (stack.last() as EntryEditorRoute).creationTransitionId)
        }
    }

    @Test
    fun hiddenHomeCallbackCannotPushOverAnotherActiveRoute() {
        val stack = mutableListOf<NavKey>(HomeRoute, SettingsRoute())
        val before = stack.toList()
        assertFalse(stack.tryLaunchHomeEditor("stale-home-action"))
        assertEquals(before, stack)
    }

    @Test
    fun unsourcedHomeActionStillCreatesTheDefaultEditorWithoutAHeroClaim() {
        val stack = mutableListOf<NavKey>(HomeRoute)
        assertTrue(stack.tryLaunchHomeEditor(null))
        assertEquals(2, stack.size)
        assertNull((stack.last() as EntryEditorRoute).creationTransitionId)
    }
}
