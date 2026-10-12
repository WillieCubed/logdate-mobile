@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.logdate.client.domain.reminders.ReminderPreferences
import app.logdate.client.domain.reminders.ReminderPreferencesState
import app.logdate.client.permissions.rememberNotificationPermissionState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_error_save_notifications
import org.jetbrains.compose.resources.getString

@Composable
fun ReminderOnboardingNotificationsStep(
    onBack: () -> Unit,
    onNext: () -> Unit,
    preferences: ReminderPreferences,
    state: ReminderPreferencesState,
    markHandled: suspend () -> Result<Unit>,
) {
    val permission = rememberNotificationPermissionState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val permissionHandled = permission.hasPermission || permission.permissionRequested
    val choice = ReminderOnboardingChoice(preferences, markHandled)

    fun choose(enabled: Boolean) {
        if (saving || permission.isRequestInFlight) return
        saving = true
        errorMessage = null
        scope.launch {
            try {
                choice
                    .choose(enabled, permissionHandled)
                    .onSuccess { next ->
                        when (next) {
                            ReminderOnboardingNext.REQUEST_PERMISSION -> permission.requestPermission()
                            ReminderOnboardingNext.CONTINUE -> onNext()
                        }
                    }.onFailure { error ->
                        Napier.e("Failed to save the onboarding reminder choice", error)
                        errorMessage = getString(Res.string.onboarding_error_save_notifications)
                    }
            } finally {
                saving = false
            }
        }
    }
    OnboardingNotificationsContent(
        onBack = onBack,
        onPrimaryAction = { choose(true) },
        onSkip = { choose(false) },
        recommendationsEnabled = true,
        hasDecision = permissionHandled,
        remindersEnabled = state.enabled,
        isSaving = saving || permission.isRequestInFlight || !state.ready,
        errorMessage = errorMessage,
    )
}
