package app.logdate.client.domain.streak

import io.github.aakira.napier.Napier

/**
 * Recalculates the current streak and persists it to the cache.
 *
 * Call this at app startup, after saving a note, or when opening the streak settings.
 */
class RefreshStreakUseCase(
    private val calculateStreakUseCase: CalculateStreakUseCase,
    private val streakSettingsRepository: StreakSettingsRepository,
) {
    suspend operator fun invoke() {
        try {
            val streak = calculateStreakUseCase()
            streakSettingsRepository.setCachedStreak(streak)
        } catch (e: Exception) {
            Napier.e("Failed to refresh streak", e)
        }
    }
}
