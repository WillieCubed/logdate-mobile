package app.logdate.shared.model

import kotlinx.serialization.Serializable

/**
 * Where a [WeekNarrative] or [YearNarrative] came from.
 *
 * The narrative shape is identical across origins so the Rewind UI doesn't branch on tier
 * — but renderers may key off this to choose between an AI-written opening and a
 * structural stats opening, or to suppress copy that would feel hollow without an LLM
 * (e.g. a templated resolution sentence).
 */
@Serializable
enum class NarrativeOrigin {
    /** Full AI narrative synthesis. */
    LLM,

    /** Local heuristic narrative with a short LLM call for highlighted quotes / reflection prompts only. */
    QUOTES_ONLY_LLM,

    /** No network call — narrative assembled entirely from local signals. */
    LOCAL_HEURISTIC,
}
