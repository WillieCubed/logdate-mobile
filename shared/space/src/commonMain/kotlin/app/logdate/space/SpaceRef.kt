package app.logdate.space

import app.logdate.lexicon.app.logdate.space.Personal

/** The kinds of space LogDate stores records in, from their space declarations in `lexicons/`. */
public object SpaceTypes {
    /** The owner's own space. Only they can read it, and every record in it is sealed. */
    public const val PERSONAL: String = Personal.TYPE
}

/**
 * A space, named the way AT Protocol Spaces names one: the DID that controls it, its type, and a
 * key that tells apart several spaces of one type.
 */
public data class SpaceRef(
    val authority: String,
    val type: String,
    val skey: String,
) {
    /** `at://{authority}/space/{type}/{skey}`. */
    val uri: String get() = "at://$authority/space/$type/$skey"

    public companion object {
        private const val DID = "did:[a-z]+:[A-Za-z0-9._:%-]+"
        private const val NSID = "[a-zA-Z][a-zA-Z0-9-]*(?:\\.[a-zA-Z0-9-]+){2,}"
        private const val SKEY = "[A-Za-z0-9._:~-]{1,512}"
        private val URI = Regex("^at://($DID)/space/($NSID)/($SKEY)$")

        /** The owner's personal space. */
        public fun personal(did: String): SpaceRef = SpaceRef(did, SpaceTypes.PERSONAL, "self")

        /** Reads a space URI, or returns null if it isn't one. */
        public fun parse(uri: String): SpaceRef? {
            val match = URI.matchEntire(uri) ?: return null
            val (authority, type, skey) = match.destructured
            return SpaceRef(authority, type, skey)
        }
    }
}
