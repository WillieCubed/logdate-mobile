package app.logdate.client.datastore.featureflags

/**
 * A feature that can be turned on or off at runtime.
 *
 * Every commit lands on `main`, so a feature that is not ready for users has to be invisible to
 * them rather than parked on a branch. A flag is how that is done: the code ships, the flag stays
 * off, and turning it on is a decision rather than a merge.
 *
 * A flag is a temporary thing. Once a feature ships to everyone, its flag and every branch on it
 * should be deleted -- a flag that nobody will ever flip is just a second way to read a constant.
 *
 * @property key The preference key the flag is stored under. Changing it silently resets everyone
 *   who had already set the flag, so treat it as permanent once released.
 * @property defaultEnabled Whether the feature is on for someone who has never set it. Incomplete
 *   work defaults to `false`; a flag that exists only as a kill switch for shipped behavior
 *   defaults to `true`.
 */
enum class FeatureFlag(
    val key: String,
    val defaultEnabled: Boolean,
) {
    /**
     * The media Library browsing experience.
     *
     * Off by default: Library is still behind the launch bar for search and error handling.
     */
    LIBRARY(key = "library_enabled", defaultEnabled = false),

    /**
     * Automatic event detection and the Events surface.
     *
     * On by default: noticing things automatically is the point of the feature, so it would be
     * odd to make someone opt in to discover it. The auto-events settings screen turns it off.
     */
    EVENTS(key = "events_enabled", defaultEnabled = true),

    /**
     * The People slice.
     *
     * On by default: intended to ship as a headline capability rather than a hidden lab feature.
     */
    PEOPLE(key = "people_enabled", defaultEnabled = true),

    /**
     * The 2.0 export archive: a self-describing folder layout with a README, readable copies of
     * every entry, portable file names and checksums.
     *
     * Off by default until restore can read the new layout. It gates only what an export writes;
     * cloud backups continue to use the current layout until an app version that reads 2.0 has shipped.
     */
    EXPORT_ARCHIVE_V2(key = "export_archive_v2_enabled", defaultEnabled = false),
    ;

    companion object {
        /**
         * Finds a flag by its stored [key], or `null` if no flag owns it.
         *
         * Useful for debug tooling that reads flags back out of storage; production code should
         * name the flag it means.
         */
        fun forKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}
