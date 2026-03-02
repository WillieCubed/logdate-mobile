package app.logdate.navigation

import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainAppNavigatorTest {

    @Test
    fun `constructor with initial route adds it to backstack`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `constructor without initial route creates empty backstack`() {
        val navigator = MainAppNavigator()

        assertEquals(0, navigator.backStack.size)
    }

    // safelyRemoveLastEntry tests

    @Test
    fun `safelyRemoveLastEntry removes last entry when backstack has multiple entries`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyRemoveLastEntry()

        assertTrue(result)
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `safelyRemoveLastEntry does nothing when backstack has single entry`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        val result = navigator.safelyRemoveLastEntry()

        assertFalse(result)
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `safelyRemoveLastEntry does nothing when backstack is empty`() {
        val navigator = MainAppNavigator()

        val result = navigator.safelyRemoveLastEntry()

        assertFalse(result)
        assertEquals(0, navigator.backStack.size)
    }

    @Test
    fun `safelyRemoveLastEntry can be called multiple times until one entry remains`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        assertTrue(navigator.safelyRemoveLastEntry())
        assertEquals(2, navigator.backStack.size)

        assertTrue(navigator.safelyRemoveLastEntry())
        assertEquals(1, navigator.backStack.size)

        assertFalse(navigator.safelyRemoveLastEntry())
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    // safelyClearBackstack tests

    @Test
    fun `safelyClearBackstack replaces entire backstack with new root`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        navigator.safelyClearBackstack(RewindList)

        assertEquals(1, navigator.backStack.size)
        assertEquals(RewindList, navigator.backStack.first())
    }

    @Test
    fun `safelyClearBackstack works on empty backstack`() {
        val navigator = MainAppNavigator()

        navigator.safelyClearBackstack(TimelineListRoute)

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `safelyClearBackstack replaces when backstack has one entry`() {
        val navigator = MainAppNavigator(JournalList)

        navigator.safelyClearBackstack(TimelineListRoute)

        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    // safelyPopBackstackTo tests

    @Test
    fun `safelyPopBackstackTo removes entries after target`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyPopBackstackTo(JournalList)

        assertTrue(result)
        assertEquals(2, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack[0])
        assertEquals(JournalList, navigator.backStack[1])
    }

    @Test
    fun `safelyPopBackstackTo returns false when target not found`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyPopBackstackTo(RewindList)

        assertFalse(result)
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun `safelyPopBackstackTo returns false when target is only entry`() {
        val navigator = MainAppNavigator(TimelineListRoute)

        val result = navigator.safelyPopBackstackTo(TimelineListRoute)

        assertFalse(result)
        assertEquals(1, navigator.backStack.size)
    }

    @Test
    fun `safelyPopBackstackTo does nothing when target is last entry`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)

        val result = navigator.safelyPopBackstackTo(JournalList)

        assertTrue(result)
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun `safelyPopBackstackTo with keepFirst moves target to front and clears rest`() {
        val navigator = MainAppNavigator(NavigationStart)
        navigator.backStack.add(TimelineListRoute)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyPopBackstackTo(TimelineListRoute, keepFirst = true)

        assertTrue(result)
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `safelyPopBackstackTo with keepFirst works when target is already first`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyPopBackstackTo(TimelineListRoute, keepFirst = true)

        assertTrue(result)
        assertEquals(1, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack.first())
    }

    @Test
    fun `safelyPopBackstackTo removes multiple entries after target`() {
        val navigator = MainAppNavigator(TimelineListRoute)
        navigator.backStack.add(JournalList)
        navigator.backStack.add(SettingsOverviewRoute)
        navigator.backStack.add(EntryEditor())

        val result = navigator.safelyPopBackstackTo(JournalList)

        assertTrue(result)
        assertEquals(2, navigator.backStack.size)
        assertEquals(TimelineListRoute, navigator.backStack[0])
        assertEquals(JournalList, navigator.backStack[1])
    }
}
