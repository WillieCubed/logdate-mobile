package app.logdate.navigation

import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.scenes.HomeTab
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the onBack handler logic in MainNavigationRoot.
 *
 * This replicates the exact logic from the onBack lambda to ensure
 * the safety checks work correctly in all scenarios.
 */
class OnBackHandlerLogicTest {

    /**
     * Replicates the onBack handler logic from MainNavigationRoot for testing.
     * This allows us to unit test the behavior without needing Compose.
     */
    private fun simulateOnBack(backStack: MutableList<NavKey>) {
        val mainTabRoutes = HomeTab.entries.map { it.route }

        // Normal case: remove a single entry
        backStack.removeLastOrNull()

        // Safety check: ensure we always have at least one main tab in the backstack
        if (backStack.isEmpty() || backStack.none { it in mainTabRoutes }) {
            backStack.clear()
            backStack.add(HomeTab.TIMELINE.route)
        }
    }

    // Empty backstack protection

    @Test
    fun `onBack prevents empty backstack when removing last entry`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack adds Timeline when backstack becomes empty after removal`() {
        val backStack = mutableStateListOf<NavKey>(EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack adds Timeline when backstack is already empty`() {
        val backStack = mutableStateListOf<NavKey>()

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    // Main tab guarantee

    @Test
    fun `onBack resets to Timeline when no main tab remains after removal`() {
        val backStack = mutableStateListOf<NavKey>(SettingsOverviewRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack preserves existing main tab when present`() {
        val backStack = mutableStateListOf<NavKey>(JournalList, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(JournalList, backStack.first())
    }

    @Test
    fun `onBack preserves main tab and keeps remaining entries`() {
        val backStack = mutableStateListOf<NavKey>(RewindList, SettingsOverviewRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(2, backStack.size)
        assertEquals(RewindList, backStack.first())
        assertEquals(SettingsOverviewRoute, backStack[1])
    }

    // Normal back navigation

    @Test
    fun `onBack removes single entry in normal case`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, JournalList, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(2, backStack.size)
        assertEquals(TimelineListRoute, backStack[0])
        assertEquals(JournalList, backStack[1])
    }

    @Test
    fun `onBack removes only one entry per action`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, JournalList, SettingsOverviewRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(3, backStack.size)
        assertEquals(TimelineListRoute, backStack[0])
        assertEquals(JournalList, backStack[1])
        assertEquals(SettingsOverviewRoute, backStack[2])
    }

    @Test
    fun `onBack removes last entry and keeps main tab`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack[0])
    }

    // Edge cases for the editor back navigation bug fix

    @Test
    fun `onBack from editor returns to Timeline when Timeline was parent`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack from editor returns to Journals when Journals was parent`() {
        val backStack = mutableStateListOf<NavKey>(JournalList, EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(JournalList, backStack.first())
    }

    @Test
    fun `onBack from editor adds Timeline when editor was only entry`() {
        val backStack = mutableStateListOf<NavKey>(EntryEditor())

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack from settings resets to Timeline when no main tab in stack`() {
        val backStack = mutableStateListOf<NavKey>(NavigationStart, SettingsOverviewRoute)

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack handles empty stack safely`() {
        val backStack = mutableStateListOf<NavKey>()

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack with two entries leaves a main tab`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, JournalList)

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack maintains backstack integrity through multiple calls`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, JournalList, SettingsOverviewRoute, EntryEditor())

        simulateOnBack(backStack)
        assertEquals(3, backStack.size)

        simulateOnBack(backStack)
        assertEquals(2, backStack.size)

        simulateOnBack(backStack)
        assertEquals(1, backStack.size)

        simulateOnBack(backStack)
        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }
}
