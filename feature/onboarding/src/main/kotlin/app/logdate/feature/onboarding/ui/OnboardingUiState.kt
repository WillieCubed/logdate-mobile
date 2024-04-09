package app.logdate.feature.onboarding.ui

import android.net.Uri
import app.logdate.core.billing.model.BackupPlanOption
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class OnboardingUiState(
    val inviteData: InviteData? = null,
    val newEntryData: NewEntryData = NewEntryData(),
    val entrySubmitted: Boolean = false,
    val planOption: BackupPlanOption? = null,
    val billingSucceeded: Boolean = false,
)

data class InviteData(
    val inviteCode: String,
    val inviteMessage: String,
)

// TODO: Please clean up this data class
data class NewEntryData(
    val recordedTimestamp: Instant = Clock.System.now(),
    val isRecordingAudio: Boolean = false,
    val transcribedSpeech: String = "",
    val recordedAudio: Uri? = null,
    val photographedImage: Uri? = null,
    val textContent: String = "",
)
