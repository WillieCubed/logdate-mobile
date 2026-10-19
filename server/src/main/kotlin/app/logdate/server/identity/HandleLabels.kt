package app.logdate.server.identity

/**
 * Rules for the first label of a hosted handle, such as `alice` in `alice.logdate.app`.
 *
 * A hosted handle is also a host on the logdate.app website, so a label the website or another
 * system already uses must never become someone's handle. Otherwise an account named `cloud` would
 * claim `cloud.logdate.app`, and one named `about` would shadow the marketing page. This list must
 * stay a superset of `RESERVED_SUBDOMAINS` in the logdate-web repository
 * (`apps/web/src/lib/site.ts`); the website refuses to resolve those labels at all.
 */
object HandleLabels {
    val RESERVED: Set<String> =
        setOf(
            // Hosts that belong to other systems or to the website itself.
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
            // Marketing and public pages served at logdate.app/<name>.
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

    /** Labels starting with `xn--` are punycode, which could display as a lookalike of another name. */
    private const val PUNYCODE_PREFIX = "xn--"
    private val invalidLabelCharacters = Regex("[^a-z0-9-]+")

    /**
     * The handle label a username maps to: lowercase, each run of characters outside `a-z0-9-`
     * (including `_`) becomes one `-`, and leading or trailing hyphens are removed. Returns an
     * empty string when nothing usable remains.
     */
    fun labelForUsername(username: String): String =
        username
            .trim()
            .lowercase()
            .replace(invalidLabelCharacters, "-")
            .trim('-')

    /** Whether [label] may never be assigned as a hosted handle label. */
    fun isReserved(label: String): Boolean {
        val normalized = label.lowercase()
        return normalized in RESERVED || normalized.startsWith(PUNYCODE_PREFIX)
    }

    /**
     * Whether a username may not be used because its handle label would be reserved. Punycode is
     * also checked with each `_` read as `-`, so `xn__name` is refused even though today's mapping
     * collapses the underscores; a later change to that mapping must not open the prefix.
     */
    fun isReservedUsername(username: String): Boolean {
        val oneToOne =
            username
                .trim()
                .lowercase()
                .replace('_', '-')
                .trimStart('-')
        return isReserved(labelForUsername(username)) || oneToOne.startsWith(PUNYCODE_PREFIX)
    }
}
