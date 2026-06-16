package app.logdate.feature.onboarding.flow

import app.logdate.client.domain.dayboundary.HealthConnectStatus

enum class OnboardingEntryMode {
    FRESH,
    CONTINUE_SETUP,
}

enum class OnboardingStep {
    PERSONAL_INTRO,
    APP_OVERVIEW,
    MEMORY_IMPORT,
    MEMORY_SELECTION,
    ACCOUNT,
    RECOVERY_PHRASE,
    BIRTHDAY,
    RECOMMENDATIONS,
    DAY_BOUNDARIES,
    LOCATION,
    NOTIFICATIONS,
    COMPLETE,
    WELCOME_BACK,
}

data class OnboardingProgressSnapshot(
    val hasPersonalIntro: Boolean = false,
    val hasBirthday: Boolean = false,
    val hasCloudAccount: Boolean = false,
    val hasIdentityKey: Boolean = false,
    val recommendationsHandledOnThisDevice: Boolean = false,
    val contextualRecommendationsEnabled: Boolean = true,
    val dayBoundariesHandledOnThisDevice: Boolean = false,
    val sleepBasedDayBoundariesEnabled: Boolean = false,
    val locationHandledOnThisDevice: Boolean = false,
    val locationTrackingEnabled: Boolean = false,
    val notificationsHandledOnThisDevice: Boolean = false,
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.CHECKING,
)

fun onboardingStepsFor(
    entryMode: OnboardingEntryMode,
    snapshot: OnboardingProgressSnapshot,
): List<OnboardingStep> =
    onboardingStepOrderFor(entryMode, snapshot.healthConnectStatus)
        .filter { step -> shouldIncludeStep(step, snapshot) }

fun firstOnboardingStep(
    entryMode: OnboardingEntryMode,
    snapshot: OnboardingProgressSnapshot,
): OnboardingStep = onboardingStepsFor(entryMode, snapshot).first()

fun nextOnboardingStepAfter(
    currentStep: OnboardingStep,
    entryMode: OnboardingEntryMode,
    snapshot: OnboardingProgressSnapshot,
): OnboardingStep? {
    val steps = onboardingStepOrderFor(entryMode, snapshot.healthConnectStatus)
    val currentIndex = steps.indexOf(currentStep)
    if (currentIndex < 0 || currentIndex == steps.lastIndex) {
        return null
    }
    return steps
        .drop(currentIndex + 1)
        .firstOrNull { step -> shouldIncludeStep(step, snapshot) }
}

private fun onboardingStepOrderFor(
    entryMode: OnboardingEntryMode,
    healthConnectStatus: HealthConnectStatus,
): List<OnboardingStep> =
    buildList {
        add(OnboardingStep.PERSONAL_INTRO)
        if (entryMode == OnboardingEntryMode.FRESH) {
            add(OnboardingStep.APP_OVERVIEW)
            add(OnboardingStep.MEMORY_IMPORT)
            add(OnboardingStep.MEMORY_SELECTION)
        }
        add(OnboardingStep.ACCOUNT)
        add(OnboardingStep.RECOVERY_PHRASE)
        add(OnboardingStep.BIRTHDAY)
        add(OnboardingStep.RECOMMENDATIONS)
        if (healthConnectStatus != HealthConnectStatus.NOT_AVAILABLE) {
            add(OnboardingStep.DAY_BOUNDARIES)
        }
        add(OnboardingStep.LOCATION)
        add(OnboardingStep.NOTIFICATIONS)
        add(terminalStepFor(entryMode))
    }

private fun shouldIncludeStep(
    step: OnboardingStep,
    snapshot: OnboardingProgressSnapshot,
): Boolean =
    when (step) {
        OnboardingStep.PERSONAL_INTRO -> !snapshot.hasPersonalIntro
        OnboardingStep.ACCOUNT -> !snapshot.hasCloudAccount
        OnboardingStep.RECOVERY_PHRASE -> !snapshot.hasIdentityKey
        OnboardingStep.BIRTHDAY -> !snapshot.hasBirthday
        OnboardingStep.RECOMMENDATIONS -> !snapshot.hasResolvedRecommendations()
        OnboardingStep.DAY_BOUNDARIES -> !snapshot.hasResolvedDayBoundaries()
        OnboardingStep.LOCATION -> !snapshot.hasResolvedLocation()
        OnboardingStep.NOTIFICATIONS -> !snapshot.notificationsHandledOnThisDevice
        else -> true
    }

fun OnboardingProgressSnapshot.canCompleteOnboarding(): Boolean =
    hasPersonalIntro &&
        hasIdentityKey &&
        hasBirthday &&
        hasResolvedRecommendations() &&
        hasResolvedDayBoundaries() &&
        hasResolvedLocation() &&
        notificationsHandledOnThisDevice

fun OnboardingProgressSnapshot.firstIncompleteRequiredOnboardingStep(): OnboardingStep? =
    when {
        !hasPersonalIntro -> OnboardingStep.PERSONAL_INTRO
        !hasIdentityKey -> OnboardingStep.RECOVERY_PHRASE
        !hasBirthday -> OnboardingStep.BIRTHDAY
        !hasResolvedRecommendations() -> OnboardingStep.RECOMMENDATIONS
        !hasResolvedDayBoundaries() -> OnboardingStep.DAY_BOUNDARIES
        !hasResolvedLocation() -> OnboardingStep.LOCATION
        !notificationsHandledOnThisDevice -> OnboardingStep.NOTIFICATIONS
        else -> null
    }

private fun OnboardingProgressSnapshot.hasResolvedRecommendations(): Boolean =
    recommendationsHandledOnThisDevice || !contextualRecommendationsEnabled

private fun OnboardingProgressSnapshot.hasResolvedDayBoundaries(): Boolean =
    healthConnectStatus == HealthConnectStatus.NOT_AVAILABLE ||
        dayBoundariesHandledOnThisDevice ||
        sleepBasedDayBoundariesEnabled

private fun OnboardingProgressSnapshot.hasResolvedLocation(): Boolean =
    locationHandledOnThisDevice ||
        locationTrackingEnabled

private fun terminalStepFor(entryMode: OnboardingEntryMode): OnboardingStep =
    when (entryMode) {
        OnboardingEntryMode.FRESH -> OnboardingStep.COMPLETE
        OnboardingEntryMode.CONTINUE_SETUP -> OnboardingStep.WELCOME_BACK
    }
