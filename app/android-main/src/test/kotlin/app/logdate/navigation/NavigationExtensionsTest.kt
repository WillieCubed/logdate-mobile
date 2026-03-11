package app.logdate.navigation

import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.LocationRoute
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.routes.core.goBack
import app.logdate.navigation.routes.core.navigateHomeFromOnboarding
import app.logdate.navigation.routes.core.openEntryEditor
import app.logdate.navigation.routes.core.switchToTab
import app.logdate.navigation.scenes.HomeTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NavigationExtensionsTest {

    // goBack tests

    @Test
    fun `goBack removes last entry when backstack has multiple entries`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(EntryEditor())

        navigator.goBack()

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `goBack navigates to Timeline when backstack has single non-main-tab entry`() {
        val navigator = MainAppNavigator(SettingsOverviewRoute)

        navigator.goBack()

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `goBack keeps existing main tab when backstack has single main tab entry`() {
        val navigator = MainAppNavigator(JournalList)

        navigator.goBack()

        assertEquals(1, navigator.backStack.size)
        assertEquals(JournalList, navigator.backStack.first())
    }

    @Test
    fun `goBack uses first main tab found when backstack has mixed entries`() {
        val navigator = MainAppNavigator(RewindList)
        navigator.backStack.clear()
        navigator.backStack.add(SettingsOverviewRoute)

        navigator.goBack()

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `goBack works correctly through multiple calls`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        navigator.goBack()
        assertEquals(2, navigator.backStack.size)
        assertEquals(JournalList, navigator.backStack.last())

        navigator.goBack()
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())

        navigator.goBack()
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    // openEntryEditor tests

    @Test
    fun `openEntryEditor adds new editor to backstack`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        navigator.openEntryEditor()

        assertEquals(2, navigator.backStack.size)
        assertTrue(navigator.backStack.last() is EntryEditor)
    }

    @Test
    fun `openEntryEditor with id adds editor with that id`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        val entryId = Uuid.random()

        navigator.openEntryEditor(entryId)

        assertEquals(2, navigator.backStack.size)
        val editor = navigator.backStack.last() as EntryEditor
        assertEquals(entryId, editor.id)
    }

    @Test
    fun `openEntryEditor without id adds editor with null id`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        navigator.openEntryEditor()

        val editor = navigator.backStack.last() as EntryEditor
        assertEquals(null, editor.id)
    }

    // navigateHomeFromOnboarding tests

    @Test
    fun `navigateHomeFromOnboarding clears backstack and sets Timeline`() {
        val navigator = MainAppNavigator(OnboardingStart)
        navigator.backStack.add(NavigationStart)

        navigator.navigateHomeFromOnboarding()

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `navigateHomeFromOnboarding works when Timeline already in backstack`() {
        val navigator = MainAppNavigator(OnboardingStart)
        navigator.backStack.add(TimelineListRoute)
        navigator.backStack.add(SettingsOverviewRoute)

        navigator.navigateHomeFromOnboarding()

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    // switchToTab tests

    @Test
    fun `switchToTab replaces current main tab with new tab`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        navigator.switchToTab(HomeTab.JOURNALS)

        assertEquals(1, navigator.backStack.size)
        assertEquals(JournalList, navigator.backStack.first())
    }

    @Test
    fun `switchToTab does nothing when already on that tab`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        navigator.switchToTab(HomeTab.TIMELINE)

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `switchToTab removes other main tabs from backstack`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        navigator.switchToTab(HomeTab.REWIND)

        assertTrue(navigator.backStack.none { it == TimelineListRoute })
        assertTrue(navigator.backStack.none { it == JournalList })
        assertEquals(RewindList, navigator.backStack.last())
    }

    @Test
    fun `switchToTab pops to existing tab if already in backstack`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        navigator.switchToTab(HomeTab.JOURNALS)

        assertEquals(2, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack[0])
        assertEquals(JournalList, navigator.backStack[1])
    }

    @Test
    fun `switchToTab from non-main-tab entry clears stack and sets new tab`() {
        val navigator = MainAppNavigator(SettingsOverviewRoute)

        navigator.switchToTab(HomeTab.TIMELINE)

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `switchToTab cycles through all tabs correctly`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        navigator.switchToTab(HomeTab.LOCATION)
        assertEquals(LocationRoute, navigator.backStack.last())

        navigator.switchToTab(HomeTab.JOURNALS)
        assertEquals(JournalList, navigator.backStack.last())

        navigator.switchToTab(HomeTab.REWIND)
        assertEquals(RewindList, navigator.backStack.last())

        navigator.switchToTab(HomeTab.TIMELINE)
        assertEquals(TimelineListRoute, navigator.backStack.last())
    }
}
