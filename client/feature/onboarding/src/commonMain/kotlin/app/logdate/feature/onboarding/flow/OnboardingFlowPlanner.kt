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
    when (entryMode) {
        OnboardingEntryMode.FRESH ->
            buildList {
                add(OnboardingStep.PERSONAL_INTRO)
                add(OnboardingStep.APP_OVERVIEW)
                add(OnboardingStep.MEMORY_IMPORT)
                add(OnboardingStep.MEMORY_SELECTION)
                add(OnboardingStep.ACCOUNT)
                add(OnboardingStep.BIRTHDAY)
                add(OnboardingStep.RECOMMENDATIONS)
                if (healthConnectStatus != HealthConnectStatus.NOT_AVAILABLE) {
                    add(OnboardingStep.DAY_BOUNDARIES)
                }
                add(OnboardingStep.LOCATION)
                add(OnboardingStep.NOTIFICATIONS)
                add(OnboardingStep.COMPLETE)
            }

        OnboardingEntryMode.CONTINUE_SETUP ->
            listOf(
                OnboardingStep.ACCOUNT,
                OnboardingStep.PERSONAL_INTRO,
                OnboardingStep.BIRTHDAY,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.WELCOME_BACK,
            )
    }

private fun shouldIncludeStep(
    step: OnboardingStep,
    snapshot: OnboardingProgressSnapshot,
): Boolean =
    when (step) {
        OnboardingStep.PERSONAL_INTRO -> !snapshot.hasPersonalIntro
        OnboardingStep.ACCOUNT -> !snapshot.hasCloudAccount
        OnboardingStep.BIRTHDAY -> !snapshot.hasBirthday
        OnboardingStep.NOTIFICATIONS -> !snapshot.notificationsHandledOnThisDevice
        else -> true
    }

fun OnboardingProgressSnapshot.canCompleteFreshOnboarding(): Boolean =
    hasPersonalIntro &&
        hasBirthday &&
        notificationsHandledOnThisDevice

fun OnboardingProgressSnapshot.firstIncompleteRequiredFreshStep(): OnboardingStep? =
    when {
        !hasPersonalIntro -> OnboardingStep.PERSONAL_INTRO
        !hasBirthday -> OnboardingStep.BIRTHDAY
        !notificationsHandledOnThisDevice -> OnboardingStep.NOTIFICATIONS
        else -> null
    }
