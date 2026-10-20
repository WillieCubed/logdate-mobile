package app.logdate.client.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalAppUpdateTest {
    @Test fun `update opens the installed app listing without opening a second activity`() {
        val urls = mutableListOf<String>()
        assertTrue(
            openLogDateUpdate("studio.hypertext.logdate") {
                urls += it
                true
            },
        )
        assertEquals(listOf("market://details?id=studio.hypertext.logdate"), urls)
    }

    @Test fun `missing store falls back to the browser for the same installed identity`() {
        val urls = mutableListOf<String>()
        assertTrue(
            openLogDateUpdate("studio.hypertext.logdate.debug") {
                urls += it
                urls.size == 2
            },
        )
        assertEquals(
            listOf(
                "market://details?id=studio.hypertext.logdate.debug",
                "https://play.google.com/store/apps/details?id=studio.hypertext.logdate.debug",
            ),
            urls,
        )
    }

    @Test fun `missing store and browser allow a copyable update link`() {
        assertFalse(openLogDateUpdate("studio.hypertext.logdate") { false })
    }
}
