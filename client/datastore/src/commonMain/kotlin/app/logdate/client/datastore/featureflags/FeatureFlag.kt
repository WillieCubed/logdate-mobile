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
    val availableForLaunch: Boolean = true,
) {
    /**
     * The media Library browsing experience.
     *
     * On by default for the first public Android release.
     */
    LIBRARY(key = "library_enabled", defaultEnabled = true),

    /**
     * Automatic event detection and the Events surface.
     *
     * Deferred from the first public Android release, including for previously enabled devices.
     */
    EVENTS(key = "events_enabled", defaultEnabled = false, availableForLaunch = false),

    /**
     * The People slice.
     *
     * Deferred from the first public Android release, including for previously enabled devices.
     */
    PEOPLE(key = "people_enabled", defaultEnabled = false, availableForLaunch = false),

    /**
     * The forgiving campfire streak that replaces the consecutive-day counter.
     *
     * On by default: every surface that shows a streak renders the campfire. The flag stays as a
     * switch back to the old counter until that counter's code is deleted, and both go together.
     */
    CAMPFIRE_STREAKS(key = "campfire_streaks_enabled", defaultEnabled = true),

    /** Combine journal membership while retaining notes and media. */
    JOURNAL_MERGE(key = "journal_merge_enabled", defaultEnabled = false),

    /** Shared adaptive Home presentation; retain the preference as a presentation rollback switch. */
    HOME_WORKSPACE_V2(key = "home_workspace_v2_enabled", defaultEnabled = true),

    /** Human history browsing and encrypted sync; complete-day recording still requires explicit opt-in. */
    HUMAN_LOCATION_HISTORY(key = "human_location_history_enabled", defaultEnabled = true),
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
