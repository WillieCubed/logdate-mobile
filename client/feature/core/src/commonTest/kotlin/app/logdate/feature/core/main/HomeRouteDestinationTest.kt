package app.logdate.feature.core.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRouteDestinationTest {
    @Test
    fun visibleEntries_hidesLibrary_whenDisabled() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = false)

        assertFalse(
            HomeRouteDestination.Library in visible,
            "Library tab must stay hidden when the library setting is disabled",
        )
    }

    @Test
    fun visibleEntries_showsLibrary_whenEnabled() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = true)

        assertTrue(
            HomeRouteDestination.Library in visible,
            "Library tab must appear when the library setting is enabled",
        )
    }

    @Test
    fun visibleEntries_preservesDeclaredOrder_whenLibraryEnabled() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = true)

        assertEquals(HomeRouteDestination.entries, visible)
    }

    @Test
    fun visibleEntries_keepsEveryNonLibraryTab_whenDisabled() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = false)

        assertEquals(
            HomeRouteDestination.entries.filterNot { it == HomeRouteDestination.Library },
            visible,
        )
    }
}
