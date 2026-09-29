package app.logdate.client.domain.rewind

import app.logdate.shared.model.Rewind
import io.github.aakira.napier.Napier

/**
 * Re-runs Rewind generation for the period of an existing Rewind.
 *
 * Drives the detail screen's "Refresh" action: useful when the AI was unavailable at
 * the original generation time and the user now has connectivity, or when the user
 * wants a fresh local Rewind built from updated curation rules. The old story remains
 * available if generation fails. A successful refresh updates its panels in one
 * transaction while keeping its identity and attached replies.
 */
class RegenerateRewindUseCase(
    private val generateBasicRewind: GenerateBasicRewindUseCase,
) {
    /**
     * @param existing The Rewind to refresh. Its `startDate` / `endDate` are reused for
     *   the new generation. It is replaced only when the new story is ready to save.
     * @return The same result shape as [GenerateBasicRewindUseCase.invoke] — `Success`
     *   with the new Rewind, `AlreadyInProgress` if a regeneration is already in flight
     *   for this period, `NoContent` if the period has nothing left to render, or `Error`.
     */
    suspend operator fun invoke(existing: Rewind): GenerateBasicRewindResult {
        Napier.d("RegenerateRewindUseCase: refreshing rewind ${existing.uid} (${existing.startDate} → ${existing.endDate})")

        return generateBasicRewind(
            startTime = existing.startDate,
            endTime = existing.endDate,
            replaceExisting = existing,
        )
    }
}
