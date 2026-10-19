package app.logdate.server.oauth

import studio.hypertext.atproto.syntax.Nsid

/**
 * The OAuth scopes this server grants and enforces.
 *
 * `atproto` alone lets an app confirm which account signed in, and nothing more. Reading or
 * writing the repository also needs `transition:generic`, and even then only collections listed in
 * [collectionsOpenToApps]. Any other requested scope is dropped at the
 * pushed authorization request, so a token's `scope` only lists what the server enforces.
 */
object OAuthScopes {
    const val ATPROTO = "atproto"
    const val TRANSITION_GENERIC = "transition:generic"

    /** Supported scopes in the order they are written into a grant. */
    val supported: List<String> = listOf(ATPROTO, TRANSITION_GENERIC)

    /** Splits a space-separated scope string into its distinct scopes. */
    fun parse(scope: String): Set<String> =
        scope
            .trim()
            .split(' ')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    /** Keeps only the [requested] scopes this server enforces, in canonical order. */
    fun granted(requested: Set<String>): List<String> = supported.filter { it in requested }

    /**
     * Collections, in canonical NSID form, that apps signed in through OAuth may read and write.
     *
     * None are open yet. Everything the repository holds today is LogDate's own journal data
     * (`studio.hypertext.logdate.*`), and the `app.logdate.*` lexicons are private journal data
     * too, so they must never be added here. A collection becomes open to apps only by being
     * listed here on purpose.
     */
    val collectionsOpenToApps: Set<String> = emptySet()

    /**
     * Returns `true` when [collection] is open to apps signed in through OAuth. The name is
     * compared in canonical NSID form, so a differently cased authority cannot slip past the list.
     */
    fun isOpenToApps(
        collection: String,
        open: Set<String> = collectionsOpenToApps,
    ): Boolean {
        val canonical = canonical(collection) ?: return false
        return canonical in open
    }

    /**
     * Returns [collections] in canonical form after checking that none is LogDate journal data.
     * Opening a `studio.hypertext.logdate` or `app.logdate` collection to apps is refused.
     */
    fun requireOpenable(collections: Set<String>): Set<String> =
        collections
            .map { collection ->
                val canonical = requireNotNull(canonical(collection)) { "$collection is not a valid NSID" }
                require(NEVER_OPEN.none(canonical::startsWith)) { "$canonical holds LogDate journal data and cannot be opened to apps" }
                canonical
            }.toSet()

    private fun canonical(collection: String): String? = Nsid.parse(collection.trim()).getOrNull()?.value

    private val NEVER_OPEN = listOf("studio.hypertext.logdate.", "app.logdate.")
}
