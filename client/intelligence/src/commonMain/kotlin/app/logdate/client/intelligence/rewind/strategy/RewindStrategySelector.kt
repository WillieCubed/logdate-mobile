package app.logdate.client.intelligence.rewind.strategy

import app.logdate.client.intelligence.availability.RewindAIAvailability
import app.logdate.client.intelligence.availability.RewindAITier

/**
 * Picks the right [RewindGenerationStrategy] for the caller's plan tier.
 *
 * Selection happens once per Rewind generation, up-front, based on
 * [RewindAIAvailability]. In-flight LLM unavailability is handled inside the chosen
 * strategy, so this selector never re-runs mid-generation.
 */
class RewindStrategySelector(
    private val availability: RewindAIAvailability,
    private val fullStrategy: FullLLMRewindStrategy,
    private val quotesOnlyStrategy: QuotesOnlyLLMRewindStrategy,
    private val localStrategy: LocalRewindStrategy,
) {
    suspend fun select(): RewindGenerationStrategy =
        when (availability.current()) {
            RewindAITier.FULL -> fullStrategy
            RewindAITier.QUOTES_ONLY -> quotesOnlyStrategy
            RewindAITier.NONE -> localStrategy
        }
}
