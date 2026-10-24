package app.logdate.server.atproto.firehose

import app.logdate.server.oauth.OAuthScopes
import studio.hypertext.atproto.syntax.Nsid

/**
 * Which collections belong to an account's public repository, the only data the server shows to
 * anyone who is not the account's owner.
 *
 * The public repository holds the profile, the sharing keys other people encrypt to, stories the
 * person chose to publish, and records other apps write for them. Everything else a person keeps in
 * LogDate is private: the `studio.hypertext.logdate.*` records of legacy sync, the
 * `app.logdate.space.*` spaces and the sealed records inside them, and every other
 * `app.logdate.*` collection. A collection is public only by being named here, so a collection
 * added to LogDate later is private until someone decides otherwise.
 *
 * @param collectionsOpenToApps collections other apps may write through OAuth. They are public
 * because an app that wrote them expects other apps to read them.
 */
class PublicRepoPolicy(
    collectionsOpenToApps: Set<String> = OAuthScopes.collectionsOpenToApps,
) {
    private val public: Set<Nsid> =
        (PUBLISHED_BY_LOGDATE + collectionsOpenToApps.map(Nsid::require)).also { collections ->
            collections.forEach { collection ->
                require(NEVER_PUBLIC_PREFIXES.none { collection.value.startsWith(it) } || collection in PUBLISHED_BY_LOGDATE) {
                    "$collection holds private LogDate data and cannot be public"
                }
            }
        }

    /** True when [collection] is part of the public repository. */
    fun isPublic(collection: Nsid): Boolean = collection in public

    /** True when every collection in [collections] is public, so a tree holding only them shows nothing private. */
    fun arePublic(collections: Collection<Nsid>): Boolean = collections.all(::isPublic)

    companion object {
        /** LogDate's profile record. */
        val PROFILE: Nsid = Nsid.require("app.logdate.actor.profile")

        /** The public key a device publishes so others can share with it. */
        val KEY_DECLARATION: Nsid = Nsid.require("app.logdate.crypto.keyDeclaration")

        /** A publication, as the Standard.site lexicons define it, that a person publishes stories under. */
        val PUBLICATION: Nsid = Nsid.require("site.standard.publication")

        /** A story a person deliberately published. */
        val DOCUMENT: Nsid = Nsid.require("site.standard.document")

        private val PUBLISHED_BY_LOGDATE: Set<Nsid> = setOf(PROFILE, KEY_DECLARATION, PUBLICATION, DOCUMENT)

        private val NEVER_PUBLIC_PREFIXES = listOf("studio.hypertext.logdate.", "app.logdate.")
    }
}
