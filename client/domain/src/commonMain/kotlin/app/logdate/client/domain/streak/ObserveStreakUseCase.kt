package app.logdate.client.domain.streak

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Observes the current streak data reactively, combining the enabled state
 * and cached streak value into a single [StreakData] flow.
 */
class ObserveStreakUseCase(
    private val streakSettingsRepository: StreakSettingsRepository,
) {
    operator fun invoke(): Flow<StreakData> =
        combine(
            streakSettingsRepository.observeStreakEnabled(),
            streakSettingsRepository.observeCachedStreak(),
        ) { enabled, cachedStreak ->
            StreakData(currentStreak = cachedStreak, isEnabled = enabled)
        }
}
