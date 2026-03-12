package app.logdate.client.domain.recommendation

import kotlinx.datetime.LocalDate

/**
 * Data from a past day worth revisiting.
 */
data class MemoryRecallData(
    val date: LocalDate,
    val summary: String,
    val people: List<String> = emptyList(),
    val mediaUris: List<String> = emptyList(),
)

/**
 * Provider for AI-generated memory recall suggestions.
 *
 * Implementations use generative AI to suggest past memories worth revisiting,
 * going beyond simple date-based heuristics. When no implementation is available,
 * [GetMemoryRecallUseCase] falls back to the "on this day" heuristic.
 */
interface AiRecallProvider {
    suspend fun suggestRecall(): MemoryRecallData?
}
