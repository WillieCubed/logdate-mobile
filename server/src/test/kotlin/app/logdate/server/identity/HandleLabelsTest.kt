package app.logdate.server.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleLabelsTest {
    /**
     * A hand-kept copy of `RESERVED_SUBDOMAINS` in logdate-web's `apps/web/src/lib/site.ts`. The
     * server must refuse each one. This repository cannot read the website's source, so the copy is
     * updated by hand whenever the website's list changes.
     */
    private val websiteReservedSubdomains =
        listOf(
            "www",
            "app",
            "cloud",
            "cloud-staging",
            "api",
            "studio",
            "admin",
            "mail",
            "blog",
            "docs",
            "status",
            "staging",
            "about",
            "contact",
            "download",
            "support",
            "report",
            "security",
            "sitemap",
            "pricing",
            "delete-account",
            "privacy",
            "terms",
        )

    @Test
    fun `every label the website reserves is reserved for handles`() {
        assertEquals(emptyList(), websiteReservedSubdomains.filterNot(HandleLabels::isReserved))
    }

    @Test
    fun `usernames map to the label their handle would use`() {
        assertEquals("delete-account", HandleLabels.labelForUsername("Delete_Account"))
        assertEquals("xn-abc", HandleLabels.labelForUsername("xn__abc"))
        assertEquals("terms", HandleLabels.labelForUsername("terms_"))
        assertEquals("", HandleLabels.labelForUsername("___"))
    }

    @Test
    fun `usernames that read as reserved or punycode labels are refused`() {
        for (username in listOf("about", "Delete_Account", "terms_", "_cloud", "xn__abc", "XN__abc", "_xn__abc")) {
            assertTrue(HandleLabels.isReservedUsername(username), username)
        }
        for (username in listOf("alice", "xn_abc", "aboutme")) {
            assertFalse(HandleLabels.isReservedUsername(username), username)
        }
    }

    @Test
    fun `punycode labels are reserved and ordinary labels are not`() {
        assertTrue(HandleLabels.isReserved("xn--lookalike"))
        assertTrue(HandleLabels.isReserved("XN--abc"))
        assertFalse(HandleLabels.isReserved("alice"))
        assertFalse(HandleLabels.isReserved("xn-alice"))
    }
}
