package app.logdate.client

import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineListRoute
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivityStartupNavigationTest {
    @Test
    fun `shouldBootstrapNavigation returns true for cold launch routes`() {
        assertTrue(shouldBootstrapNavigation(null))
        assertTrue(shouldBootstrapNavigation(NavigationStart))
    }

    @Test
    fun `shouldBootstrapNavigation returns false for restored entry editor`() {
        assertFalse(shouldBootstrapNavigation(EntryEditor()))
    }

    @Test
    fun `shouldBootstrapNavigation returns false for restored non-home routes`() {
        assertFalse(shouldBootstrapNavigation(SettingsOverviewRoute))
        assertFalse(shouldBootstrapNavigation(TimelineListRoute))
    }
}
