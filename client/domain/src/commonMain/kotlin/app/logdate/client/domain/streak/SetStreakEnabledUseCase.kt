package app.logdate.client.domain.streak

/**
 * Toggles streak tracking on or off.
 */
class SetStreakEnabledUseCase(
    private val streakSettingsRepository: StreakSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        streakSettingsRepository.setStreakEnabled(enabled)
    }
}
