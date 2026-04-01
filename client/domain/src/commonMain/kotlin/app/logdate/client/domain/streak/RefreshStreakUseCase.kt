package app.logdate.client.domain.streak

import app.logdate.client.repository.streak.StreakSettingsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recalculates the current streak and persists it to the cache.
 *
 * Uses a mutex to coalesce concurrent calls — if multiple ViewModels
 * trigger a refresh simultaneously, only one calculation runs at a time.
 */
class RefreshStreakUseCase(
    private val calculateStreakUseCase: CalculateStreakUseCase,
    private val streakSettingsRepository: StreakSettingsRepository,
) {
    private val refreshMutex = Mutex()

    suspend operator fun invoke() {
        refreshMutex.withLock {
            try {
                val streak = calculateStreakUseCase()
                streakSettingsRepository.setCachedStreak(streak)
            } catch (e: Exception) {
                Napier.e("Failed to refresh streak", e)
            }
        }
    }
}
