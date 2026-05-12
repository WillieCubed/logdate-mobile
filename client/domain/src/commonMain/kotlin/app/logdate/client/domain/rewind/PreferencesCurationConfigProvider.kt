package app.logdate.client.domain.rewind

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.intelligence.curation.CurationConfig
import app.logdate.client.intelligence.curation.CurationConfigProvider

/**
 * [CurationConfigProvider] backed by the user's stored Rewind preferences.
 *
 * Reads the persisted strictness value and screenshot-inclusion toggle, then maps the
 * combination to a concrete [CurationConfig]. An unparseable strictness value (the user
 * has a row from an older release with a name we no longer ship) falls back to STANDARD
 * rather than erroring — the goal is "always produce a usable Rewind".
 */
class PreferencesCurationConfigProvider(
    private val preferences: LogdatePreferencesDataSource,
) : CurationConfigProvider {
    override suspend fun get(): CurationConfig {
        val strictness =
            runCatching { CurationConfig.Strictness.valueOf(preferences.getRewindCurationStrictness()) }
                .getOrDefault(CurationConfig.Strictness.STANDARD)
        val includeScreenshots = preferences.isRewindIncludeScreenshots()
        val base = CurationConfig.forStrictness(strictness)
        return if (includeScreenshots) base.copy(excludeScreenshots = false) else base
    }
}
