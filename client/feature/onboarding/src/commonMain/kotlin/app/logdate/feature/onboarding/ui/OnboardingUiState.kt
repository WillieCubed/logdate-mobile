package app.logdate.feature.onboarding.ui

import app.logdate.client.billing.model.LogDateBackupPlanOption
import kotlin.time.Instant

data class OnboardingUiState(
    val inviteData: InviteData? = null,
    val newEntryData: NewEntryData = NewEntryData(),
    val entrySubmitted: Boolean = false,
    val planOption: LogDateBackupPlanOption? = null,
    val billingSucceeded: Boolean = false,
)

data class InviteData(
    val inviteCode: String,
    val inviteMessage: String,
)

// TODO: Please clean up this data class
data class NewEntryData(
    val timestamp: Instant = Instant.DISTANT_PAST,
    val photographedImage: String? = null,
    val textContent: String = "",
)
