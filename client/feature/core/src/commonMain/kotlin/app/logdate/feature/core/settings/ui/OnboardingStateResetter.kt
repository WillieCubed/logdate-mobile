package app.logdate.feature.core.settings.ui

/**
 * Clears device-local onboarding progress.
 *
 * `client/feature/core` cannot depend on `client/feature/onboarding` (the dependency runs
 * the other way), so [DangerZoneSettingsViewModel] resets onboarding progress through this
 * seam instead of the repository directly -- the onboarding module binds the real
 * implementation.
 */
fun interface OnboardingStateResetter {
    suspend fun clear()
}
