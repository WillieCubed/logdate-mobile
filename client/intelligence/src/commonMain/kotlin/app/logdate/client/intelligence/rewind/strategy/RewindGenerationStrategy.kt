package app.logdate.client.intelligence.rewind.strategy

import app.logdate.client.intelligence.AIUnavailableReason
import app.logdate.client.intelligence.availability.RewindAITier
import app.logdate.client.intelligence.curation.CurationResult
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.shared.model.Person
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.RewindMetadata
import app.logdate.shared.model.WeatherContext
import app.logdate.shared.model.WeekNarrative
import kotlin.time.Instant

/**
 * Pre-flight strategy that turns raw journal + media + people + location into a fully
 * shaped Rewind. Three implementations cover the three [RewindAITier]s.
 *
 * Each strategy is responsible for its own fallback when an in-flight LLM call returns
 * `AIResult.Unavailable` — so the caller (`GenerateBasicRewindUseCase`) doesn't have to
 * branch on tier or retry state. The [RewindStrategySelector] picks the strategy once,
 * based on the entitlement; subsequent unavailability is internal to the strategy.
 */
interface RewindGenerationStrategy {
    /** Short human-readable identifier used in [GenerationProvenance.strategy]. */
    val name: String

    /** Produces the shaped Rewind. Never throws; failures surface in [RewindStrategyOutput]. */
    suspend fun produce(input: RewindInput): RewindStrategyOutput
}

/**
 * Everything a strategy needs to produce a Rewind. Built once by the use case and shared
 * across the chosen strategy and any internal fallback.
 */
data class RewindInput(
    val periodStart: Instant,
    val periodEnd: Instant,
    val textEntries: List<JournalNote.Text>,
    val media: List<IndexedMedia>,
    val people: List<Person>,
    val locationHistory: List<LocationHistoryItem>,
    val weekId: String,
)

/**
 * Output of one strategy run. The use case persists [content] and [metadata]; the rest
 * is provenance / telemetry.
 */
data class RewindStrategyOutput(
    /**
     * Narrative the strategy produced. LLM strategies produce an AI-written one; the
     * local strategy produces a templated [WeekNarrative] with origin `LOCAL_HEURISTIC`.
     * Never null — the strategy always returns a narrative so the sequencer has
     * something to compose against.
     */
    val narrative: WeekNarrative,
    /** Curated media — what the sequencer rendered. */
    val curation: CurationResult,
    /** Final panel list for the Rewind. */
    val content: List<RewindContent>,
    /** Intelligence metadata for the Rewind row. */
    val metadata: RewindMetadata,
    /** Trace of what ran and why. */
    val provenance: GenerationProvenance,
    /** Weather context (best-effort). */
    val weatherContext: WeatherContext? = null,
)

/**
 * Why a Rewind ended up looking the way it does. Logged for now; future surface could
 * expose a "Why this Rewind?" affordance from this.
 */
data class GenerationProvenance(
    /** Concrete strategy that produced the Rewind (e.g. `full-llm`, `local-heuristic`). */
    val strategy: String,
    /** Tier resolved by [app.logdate.client.intelligence.availability.RewindAIAvailability]. */
    val tier: RewindAITier,
    /** Reason an LLM call was unavailable, when the strategy fell through. */
    val aiUnavailableReason: AIUnavailableReason? = null,
    /** Number of LLM calls actually issued by this strategy. */
    val llmCalls: Int = 0,
    /**
     * If a strategy degraded to a different one mid-flight, this records the original
     * choice (e.g. `full-llm` when QuotesOnly fell through to local).
     */
    val fellBackFrom: String? = null,
)
