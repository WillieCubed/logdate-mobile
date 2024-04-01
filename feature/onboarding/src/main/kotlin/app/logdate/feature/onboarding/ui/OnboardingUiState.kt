package app.logdate.feature.onboarding.ui

data class OnboardingUiState(
    val inviteData: InviteData? = null,
)

data class InviteData(
    val inviteCode: String,
    val inviteMessage: String,
)
