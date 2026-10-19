package app.logdate.server.oauth

/**
 * The OAuth scopes this server grants and enforces.
 *
 * `atproto` alone lets an app confirm which account signed in, and nothing more. Writing records
 * and uploading files also needs `transition:generic`. Any other requested scope is dropped at the
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
     * Collections that hold the LogDate journal. Apps signed in through OAuth never see or change
     * them, whatever scope they hold.
     */
    const val PRIVATE_COLLECTION_PREFIX = "studio.hypertext.logdate."

    /** Returns `true` when [collection] is part of the private LogDate journal. */
    fun isPrivateCollection(collection: String): Boolean = collection.startsWith(PRIVATE_COLLECTION_PREFIX)
}
