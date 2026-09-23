package app.logdate.feature.onboarding.flow

import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.dayboundary.ObserveHealthConnectStatusUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first

/**
 * The completion check terminal onboarding screens (the welcome-back and completion screens)
 * need is a one-shot read, not a live reactive view -- unlike [OnboardingDeviceStateRepository]'s
 * other consumers, which re-render as gating inputs change mid-flow. Reading each input's current
 * value directly here avoids standing up the same combine chain as a permanently-subscribed,
 * eagerly-shared [kotlinx.coroutines.flow.StateFlow] just for a check made once, at exit.
 */
class OnboardingCompletionCoordinator(
    private val observeUserIdentity: ObserveUserIdentityUseCase,
    private val onboardingDeviceStateRepository: OnboardingDeviceStateRepository,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository,
    private val observeHealthConnectStatus: ObserveHealthConnectStatusUseCase,
    private val userStateRepository: UserStateRepository,
    private val refreshStreakUseCase: RefreshStreakUseCase,
) {
    /**
     * Saves that onboarding is complete, unless a required step is still incomplete.
     *
     * Callers leave onboarding only on [OnboardingFinishResult.Finished]. Leaving after a failed
     * save would put the user on Home with the onboarded flag still false, and the next activity
     * recreation would send them back into onboarding.
     */
    suspend fun finishOnboarding(): OnboardingFinishResult {
        currentProgressSnapshot().firstIncompleteRequiredOnboardingStep()?.let { step ->
            return OnboardingFinishResult.IncompleteStep(step)
        }

        runCatching { userStateRepository.setIsOnboardingComplete(true) }
            .onFailure { error ->
                Napier.e("Failed to save onboarding completion", error)
                return OnboardingFinishResult.SaveFailed(error)
            }

        refreshStreakUseCase()
        return OnboardingFinishResult.Finished
    }

    private suspend fun currentProgressSnapshot(): OnboardingProgressSnapshot {
        val identity = observeUserIdentity().first()
        val deviceState = onboardingDeviceStateRepository.deviceState.value
        val healthStatus = observeHealthConnectStatus().first()
        val recommendationsSettings = memoriesSettingsRepository.observeSettings().first()
        val locationSettings = locationTrackingSettingsRepository.observeSettings().first()
        val dayBoundarySettings = dayBoundarySettingsRepository.observeSettings().first()

        return OnboardingProgressSnapshot(
            // Only the name is asked for; a blank bio must not send someone back through the
            // introduction every time they open the app.
            hasPersonalIntro = identity.displayName.isNotBlank(),
            hasBirthday = identity.birthday != null,
            hasCloudAccount =
                identity.isAuthenticated ||
                    identity.cloudAccountId != null ||
                    !identity.username.isNullOrBlank(),
            accountHandledOnThisDevice = deviceState.accountHandledOnThisDevice,
            recommendationsHandledOnThisDevice = deviceState.recommendationsHandledOnThisDevice,
            contextualRecommendationsEnabled = recommendationsSettings.contextualRecommendationsEnabled,
            dayBoundariesHandledOnThisDevice = deviceState.dayBoundariesHandledOnThisDevice,
            sleepBasedDayBoundariesEnabled = dayBoundarySettings.sleepBasedBoundariesEnabled,
            locationHandledOnThisDevice = deviceState.locationHandledOnThisDevice,
            locationTrackingEnabled = locationSettings.backgroundTrackingEnabled,
            notificationsHandledOnThisDevice = deviceState.notificationsHandledOnThisDevice,
            healthConnectStatus = healthStatus,
        )
    }
}

/** The outcome of [OnboardingCompletionCoordinator.finishOnboarding]. */
sealed interface OnboardingFinishResult {
    /** The onboarded flag is saved; the caller can leave onboarding. */
    data object Finished : OnboardingFinishResult

    /** A required [step] is still incomplete; the caller routes there instead. */
    data class IncompleteStep(
        val step: OnboardingStep,
    ) : OnboardingFinishResult

    /** Saving the onboarded flag failed; the caller stays put and offers a retry. */
    data class SaveFailed(
        val error: Throwable,
    ) : OnboardingFinishResult
}
