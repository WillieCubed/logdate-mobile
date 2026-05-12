package app.logdate.client.intelligence.rewind.strategy

import app.logdate.client.intelligence.availability.RewindAITier

/**
 * Mid-tier strategy that uses a single short LLM call for highlighted quotes / reflection
 * prompts but assembles the rest of the narrative locally. Falls through to
 * [LocalRewindStrategy] when the LLM is unavailable.
 *
 * The Step 6 placeholder shape always defers to [LocalRewindStrategy] until the
 * dedicated quotes-only prompt lands. The strategy is wired in so the tier-aware
 * selector picks something correct for STANDARD-plan users today (they'll get the
 * local experience), and the upgrade to a real quotes-only LLM call is a code-only
 * change in this class — no caller migration required.
 */
class QuotesOnlyLLMRewindStrategy(
    private val localFallback: LocalRewindStrategy,
) : RewindGenerationStrategy {
    override val name: String = STRATEGY_NAME

    override suspend fun produce(input: RewindInput): RewindStrategyOutput {
        val local = localFallback.produce(input)
        return local.copy(
            provenance =
                local.provenance.copy(
                    strategy = STRATEGY_NAME,
                    tier = RewindAITier.QUOTES_ONLY,
                    fellBackFrom = STRATEGY_NAME,
                ),
        )
    }

    private companion object {
        const val STRATEGY_NAME = "quotes-only-llm"
    }
}
