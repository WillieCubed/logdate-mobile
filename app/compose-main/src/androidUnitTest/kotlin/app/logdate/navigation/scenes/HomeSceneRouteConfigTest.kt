package app.logdate.navigation.scenes

import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HomeSceneRouteConfigTest {

    @Test
    fun `classifyRoute returns MainTab for Timeline list`() {
        val result = RouteConfig.classifyRoute(TimelineListRoute::class)

        val mainTab = assertIs<RouteClassification.MainTab>(result)
        assertEquals(HomeTab.TIMELINE, mainTab.tab)
    }

    @Test
    fun `classifyRoute returns Excluded for settings routes`() {
        val result = RouteConfig.classifyRoute(SettingsOverviewRoute::class)

        assertIs<RouteClassification.Excluded>(result)
    }

    @Test
    fun `classifyRoute returns TwoPaneDetail for timeline detail with timeline parent`() {
        val result = RouteConfig.classifyRoute(
            TimelineDetail::class,
            previousRouteClass = TimelineListRoute::class
        )

        val twoPane = assertIs<RouteClassification.TwoPaneDetail>(result)
        assertEquals(HomeTab.TIMELINE, twoPane.parentTab)
    }

    @Test
    fun `classifyRoute returns FullscreenDetail for rewind detail`() {
        val result = RouteConfig.classifyRoute(RewindDetailRoute::class)

        assertIs<RouteClassification.FullscreenDetail>(result)
    }

    @Test
    fun `isAlwaysFullscreen returns true for rewind and journal detail`() {
        assertTrue(RouteConfig.isAlwaysFullscreen(RewindDetailRoute::class))
        assertTrue(RouteConfig.isAlwaysFullscreen(JournalDetail::class))
    }

    @Test
    fun `isAlwaysFullscreen returns false for timeline detail`() {
        assertFalse(RouteConfig.isAlwaysFullscreen(TimelineDetail::class))
    }
}
