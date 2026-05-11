package app.logdate.client.intelligence.availability

/**
 * Tier of AI capability available to Rewind generation right now.
 *
 * Selected by [RewindAIAvailability] before generation starts so a generator can pick
 * the right strategy (full LLM narrative, quotes-only LLM, or pure local heuristics)
 * without branching on plan / network / credential state itself.
 */
enum class RewindAITier {
    /** No LLM available — the local heuristic strategy must produce the whole Rewind. */
    NONE,

    /**
     * Short LLM calls allowed (highlighted quotes / reflection prompts) but the narrative
     * itself is assembled from local signals.
     */
    QUOTES_ONLY,

    /** Full narrative synthesis via the LLM. */
    FULL,
}

/**
 * Resolves the [RewindAITier] available for the current session.
 *
 * Pre-flight check separate from `AIResult.Unavailable`: tier selection runs before any
 * LLM call to pick a strategy; `AIResult.Unavailable` is what happens when a call inside
 * the selected strategy fails. Strategies fall through internally when an in-flight call
 * comes back unavailable.
 */
interface RewindAIAvailability {
    /**
     * Current tier. Suspend because the underlying signal may require a network read
     * (entitlement fetch).
     */
    suspend fun current(): RewindAITier
}
