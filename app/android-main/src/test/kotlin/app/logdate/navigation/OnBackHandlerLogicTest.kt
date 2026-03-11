package app.logdate.navigation

import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.scenes.HomeTab
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

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
     *
     * Matches the actual implementation which uses safelyRemoveLastEntry()
     * (only removes if size > 1) followed by a main tab safety check.
     */
    private fun simulateOnBack(backStack: MutableList<NavKey>) {
        val mainTabRoutes = HomeTab.entries.map { it.route }

        // Matches safelyRemoveLastEntry(): only remove if size > 1
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }

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

    // TwoPaneDetail back navigation (the core bug scenario)

    @Test
    fun `onBack from timeline detail returns to timeline list`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute, TimelineDetail(LocalDate(2026, 3, 10)))

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `onBack from journal detail returns to journal list`() {
        val backStack = mutableStateListOf<NavKey>(JournalList, JournalDetail(Uuid.random()))

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(JournalList, backStack.first())
    }

    @Test
    fun `onBack does not remove sole remaining entry`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute)

        simulateOnBack(backStack)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    // switchToTab tests

    /**
     * Replicates the switchToTab logic from NavigationExtensions for testing.
     * This mirrors the actual implementation to verify behavior without Compose.
     */
    private fun simulateSwitchToTab(backStack: MutableList<NavKey>, tab: HomeTab) {
        val existingTabIndex = backStack.indexOfFirst { it == tab.route }

        if (existingTabIndex >= 0) {
            // Tab already in stack — pop everything after it
            if (existingTabIndex < backStack.size - 1) {
                while (backStack.size > existingTabIndex + 1) {
                    backStack.removeLastOrNull()
                }
            }
        } else {
            val currentEntry = backStack.lastOrNull()
            val isCurrentEntryMainTab = currentEntry != null && HomeTab.entries.any { it.route == currentEntry }

            if (isCurrentEntryMainTab && backStack.size > 0) {
                // Replace the current tab
                val otherMainTabsIndices = backStack.mapIndexedNotNull { index, entry ->
                    if (entry != currentEntry && HomeTab.entries.any { it.route == entry }) index else null
                }
                backStack.removeLastOrNull()
                backStack.add(tab.route)
                otherMainTabsIndices.sortedDescending().forEach { index ->
                    if (index < backStack.size) backStack.removeAt(index)
                }
            } else {
                // Current top is not a main tab — clear and set new tab
                backStack.clear()
                backStack.add(tab.route)
            }
        }
    }

    @Test
    fun `switchToTab from detail does not orphan detail view`() {
        // Scenario: on tablet, viewing timeline detail in two-pane mode, then switch to Journals
        val backStack = mutableStateListOf<NavKey>(
            TimelineListRoute,
            TimelineDetail(LocalDate(2026, 3, 10)),
        )

        simulateSwitchToTab(backStack, HomeTab.JOURNALS)

        // Should have only the new tab, no orphaned detail
        assertEquals(1, backStack.size)
        assertEquals(JournalList, backStack.first())
        assertFalse(backStack.any { it is TimelineDetail })
    }

    @Test
    fun `switchToTab replaces current tab when top is a main tab`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute)

        simulateSwitchToTab(backStack, HomeTab.JOURNALS)

        assertEquals(1, backStack.size)
        assertEquals(JournalList, backStack.first())
    }

    @Test
    fun `switchToTab does nothing when target tab is already on top`() {
        val backStack = mutableStateListOf<NavKey>(TimelineListRoute)

        simulateSwitchToTab(backStack, HomeTab.TIMELINE)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }

    @Test
    fun `switchToTab pops to existing tab removing entries above it`() {
        val backStack = mutableStateListOf<NavKey>(
            TimelineListRoute,
            TimelineDetail(LocalDate(2026, 3, 10)),
        )

        simulateSwitchToTab(backStack, HomeTab.TIMELINE)

        assertEquals(1, backStack.size)
        assertEquals(TimelineListRoute, backStack.first())
    }
}
