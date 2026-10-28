package app.logdate.feature.core.main

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRouteDestinationTest {
    @Test
    fun `Places is hidden until explicitly enabled without hiding Rewind`() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = true, isPlacesEnabled = false)
        assertFalse(HomeRouteDestination.LocationHistory in HomeRouteDestination.visibleEntries(true))
        assertFalse(HomeRouteDestination.LocationHistory in visible)
        assertTrue(HomeRouteDestination.Rewind in visible)
        assertEquals(HomeRouteDestination.entries, HomeRouteDestination.visibleEntries(true, true))
    }

    @Test
    fun `Places and Library can be hidden independently`() {
        assertEquals(
            listOf(HomeRouteDestination.Timeline, HomeRouteDestination.Journals, HomeRouteDestination.Rewind),
            HomeRouteDestination.visibleEntries(false, false),
        )
        assertTrue(HomeRouteDestination.LocationHistory in HomeRouteDestination.visibleEntries(false, true))
    }

    @Test
    fun `visible entries hides library when disabled`() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = false, isPlacesEnabled = true)

        assertFalse(
            HomeRouteDestination.Library in visible,
            "Library tab must stay hidden when the library setting is disabled",
        )
    }

    @Test
    fun `visible entries shows library when enabled`() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = true, isPlacesEnabled = true)

        assertTrue(
            HomeRouteDestination.Library in visible,
            "Library tab must appear when the library setting is enabled",
        )
    }

    @Test
    fun `visible entries preserves declared order when library enabled`() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = true, isPlacesEnabled = true)

        assertEquals(HomeRouteDestination.entries, visible)
    }

    @Test
    fun `visible entries keeps every non library tab when disabled`() {
        val visible = HomeRouteDestination.visibleEntries(isLibraryEnabled = false, isPlacesEnabled = true)

        assertEquals(
            HomeRouteDestination.entries.filterNot { it == HomeRouteDestination.Library },
            visible,
        )
    }
}
