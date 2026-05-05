package app.logdate.client.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchRouteDeepLinkTest {
    @Test
    fun `all-empty input produces a default SearchRoute`() {
        val route = searchRouteFromParams(null, null, null)
        assertEquals(SearchRoute(), route)
    }

    @Test
    fun `each param maps to its corresponding SearchRoute field`() {
        val route =
            searchRouteFromParams(
                rawQuery = "redwoods",
                rawTypes = "text_note,media_caption",
                rawDate = "Today",
            )
        assertEquals("redwoods", route.query)
        assertEquals(listOf("text_note", "media_caption"), route.typeFtsValues)
        assertEquals("Today", route.dateRangeName)
    }

    @Test
    fun `type list trims whitespace and drops empty segments`() {
        val route =
            searchRouteFromParams(
                rawQuery = null,
                rawTypes = " text_note , , media_caption ,",
                rawDate = null,
            )
        assertEquals(listOf("text_note", "media_caption"), route.typeFtsValues)
    }

    @Test
    fun `single type with no commas is parsed into a one-element list`() {
        val route = searchRouteFromParams(null, "rewind", null)
        assertEquals(listOf("rewind"), route.typeFtsValues)
    }

    @Test
    fun `empty raw strings become empty fields not nulls`() {
        val route = searchRouteFromParams("", "", "")
        assertEquals(SearchRoute(), route)
    }
}
