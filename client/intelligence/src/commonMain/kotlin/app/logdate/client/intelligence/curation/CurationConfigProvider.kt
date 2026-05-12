package app.logdate.client.intelligence.curation

/**
 * Resolves the [CurationConfig] for the current user.
 *
 * Implementations typically read the user's curation-strictness preference and the
 * screenshot-inclusion toggle, then map those to a [CurationConfig] via
 * [CurationConfig.forStrictness]. A default fallback implementation returns the STANDARD
 * preset so callers without DI wiring still get a sensible config.
 */
interface CurationConfigProvider {
    /** Resolves the active config. Suspends because the underlying preference read is async. */
    suspend fun get(): CurationConfig

    companion object {
        /** Always returns the STANDARD config — used as a fallback when no preference store
         *  is wired in. */
        val Default: CurationConfigProvider =
            object : CurationConfigProvider {
                override suspend fun get(): CurationConfig = CurationConfig.forStrictness(CurationConfig.Strictness.STANDARD)
            }
    }
}
