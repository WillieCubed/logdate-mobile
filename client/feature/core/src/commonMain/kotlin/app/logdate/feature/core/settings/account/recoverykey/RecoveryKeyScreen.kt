@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.recoverykey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.recovery_key_dismiss
import logdate.client.feature.core.generated.resources.recovery_key_error_already_set
import logdate.client.feature.core.generated.resources.recovery_key_error_device
import logdate.client.feature.core.generated.resources.recovery_key_error_identity_changed
import logdate.client.feature.core.generated.resources.recovery_key_error_key_removed
import logdate.client.feature.core.generated.resources.recovery_key_error_not_available
import logdate.client.feature.core.generated.resources.recovery_key_error_not_set
import logdate.client.feature.core.generated.resources.recovery_key_error_passkey_required
import logdate.client.feature.core.generated.resources.recovery_key_error_phrase_mismatch
import logdate.client.feature.core.generated.resources.recovery_key_error_refused
import logdate.client.feature.core.generated.resources.recovery_key_error_server_key_missing
import logdate.client.feature.core.generated.resources.recovery_key_error_signature_rejected
import logdate.client.feature.core.generated.resources.recovery_key_error_signed_out
import logdate.client.feature.core.generated.resources.recovery_key_error_step_up_cancelled
import logdate.client.feature.core.generated.resources.recovery_key_error_step_up_failed
import logdate.client.feature.core.generated.resources.recovery_key_error_step_up_unavailable
import logdate.client.feature.core.generated.resources.recovery_key_error_unexpected_change
import logdate.client.feature.core.generated.resources.recovery_key_error_unreachable
import logdate.client.feature.core.generated.resources.recovery_key_from_phrase
import logdate.client.feature.core.generated.resources.recovery_key_intro
import logdate.client.feature.core.generated.resources.recovery_key_limits
import logdate.client.feature.core.generated.resources.recovery_key_load_failed
import logdate.client.feature.core.generated.resources.recovery_key_not_offered
import logdate.client.feature.core.generated.resources.recovery_key_on_body
import logdate.client.feature.core.generated.resources.recovery_key_on_title
import logdate.client.feature.core.generated.resources.recovery_key_other_phrase_body
import logdate.client.feature.core.generated.resources.recovery_key_other_phrase_title
import logdate.client.feature.core.generated.resources.recovery_key_phrase_cannot_derive
import logdate.client.feature.core.generated.resources.recovery_key_phrase_confirmation_unreadable
import logdate.client.feature.core.generated.resources.recovery_key_phrase_confirmed
import logdate.client.feature.core.generated.resources.recovery_key_phrase_missing
import logdate.client.feature.core.generated.resources.recovery_key_phrase_not_confirmed
import logdate.client.feature.core.generated.resources.recovery_key_phrase_step_title
import logdate.client.feature.core.generated.resources.recovery_key_phrase_unreadable
import logdate.client.feature.core.generated.resources.recovery_key_recover_action
import logdate.client.feature.core.generated.resources.recovery_key_recover_body
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_intro
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_check
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_passkey
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_rank
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_replace
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_unchanged
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_recover_section
import logdate.client.feature.core.generated.resources.recovery_key_recovered
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_action
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_body
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_body
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_needs_phrase
import logdate.client.feature.core.generated.resources.recovery_key_replace_key_section
import logdate.client.feature.core.generated.resources.recovery_key_replaced_key
import logdate.client.feature.core.generated.resources.recovery_key_setup_section
import logdate.client.feature.core.generated.resources.recovery_key_status_section
import logdate.client.feature.core.generated.resources.recovery_key_title
import logdate.client.feature.core.generated.resources.recovery_key_try_again
import logdate.client.feature.core.generated.resources.recovery_key_turn_on
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_body
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_turn_on_needs_phrase
import logdate.client.feature.core.generated.resources.recovery_key_warning
import logdate.client.feature.core.generated.resources.recovery_key_what_it_does
import logdate.client.feature.core.generated.resources.recovery_key_without_phrase_body
import logdate.client.feature.core.generated.resources.recovery_key_without_phrase_title
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Where the Recovery key screen leads. It never leads to phrase entry: entering a phrase replaces
 * the one that unlocks this device's journal, which is not something this page should start.
 */
data class RecoveryKeyDestinations(
    val onBack: () -> Unit,
    /** Opens the Recovery phrase page, where the person reveals, saves and confirms their phrase. */
    val onRecoveryPhrase: () -> Unit,
)

/** Sets up the person's recovery key, uses it to replace the server's key, and replaces a lost one. */
@Composable
fun RecoveryKeyScreen(
    destinations: RecoveryKeyDestinations,
    viewModel: RecoveryKeyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Coming back from saving the phrase finds it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    RecoveryKeyContent(
        state = state,
        destinations = destinations,
        actions =
            RecoveryKeyActions(
                onTurnOn = viewModel::turnOn,
                onReplaceServerKey = viewModel::replaceServerKey,
                onReplaceRecoveryKey = viewModel::replaceRecoveryKey,
                onRetry = viewModel::refresh,
                onDismissError = viewModel::dismissError,
            ),
    )
}

/** What the person can ask the Recovery key screen to do. */
data class RecoveryKeyActions(
    val onTurnOn: () -> Unit = {},
    val onReplaceServerKey: () -> Unit = {},
    val onReplaceRecoveryKey: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
fun RecoveryKeyContent(
    state: RecoveryKeyUiState,
    destinations: RecoveryKeyDestinations,
    actions: RecoveryKeyActions,
) {
    var confirmTurnOn by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmReplaceKey by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(Res.string.recovery_key_title), onBack = destinations.onBack) {
        when (state) {
            RecoveryKeyUiState.Loading ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

            RecoveryKeyUiState.NotOffered ->
                item { Paragraph(stringResource(Res.string.recovery_key_not_offered)) }

            is RecoveryKeyUiState.LoadFailed ->
                item { ProblemWithRetry(stringResource(Res.string.recovery_key_load_failed, state.serverName), actions.onRetry) }

            is RecoveryKeyUiState.PhraseUnavailable -> {
                item { Explanation(state.handle, state.serverName) }
                item { ProblemWithRetry(phraseProblemMessage(state.problem), actions.onRetry) }
            }

            is RecoveryKeyUiState.Ready -> {
                item { Explanation(state.handle, state.serverName) }
                state.error?.let { failure -> item { ErrorNotice(state, failure, actions.onDismissError) } }
                if (state.replacedServerKey) {
                    item { Notice(stringResource(Res.string.recovery_key_recovered, state.serverName, state.handle)) }
                }
                if (state.replacedRecoveryKey) {
                    item { Notice(stringResource(Res.string.recovery_key_replaced_key, state.handle)) }
                }
                if (state.key == KeyStatus.OFF) {
                    item { SetupSection(state, destinations) }
                    item { TurnOnAction(state, onTurnOn = { confirmTurnOn = true }) }
                } else {
                    item { StatusSection(state) }
                    if (state.key == KeyStatus.ON) {
                        item { ReplaceServerKeySection(state, onReplace = { confirmReplace = true }) }
                    }
                    if (state.key == KeyStatus.ON_FROM_OTHER_PHRASE) {
                        item { ReplaceRecoveryKeySection(state, destinations, onReplace = { confirmReplaceKey = true }) }
                    }
                }
            }
        }
    }

    val ready = state as? RecoveryKeyUiState.Ready ?: return
    if (confirmTurnOn) {
        TurnOnRecoveryKeyDialog(
            state = ready,
            onConfirm = {
                confirmTurnOn = false
                actions.onTurnOn()
            },
            onDismiss = { confirmTurnOn = false },
        )
    }
    if (confirmReplace) {
        ReplaceServerKeyDialog(
            state = ready,
            onConfirm = {
                confirmReplace = false
                actions.onReplaceServerKey()
            },
            onDismiss = { confirmReplace = false },
        )
    }
    if (confirmReplaceKey) {
        ReplaceRecoveryKeyDialog(
            state = ready,
            onConfirm = {
                confirmReplaceKey = false
                actions.onReplaceRecoveryKey()
            },
            onDismiss = { confirmReplaceKey = false },
        )
    }
}

/** Says what turning on the recovery key changes before anything is sent. */
@Composable
fun TurnOnRecoveryKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_turn_on_confirm_title),
        text = { Text(stringResource(Res.string.recovery_key_turn_on_confirm_body, state.serverName, state.handle)) },
        confirmLabel = stringResource(Res.string.recovery_key_turn_on_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Lists what replacing the server's key will do before anything is sent. */
@Composable
fun ReplaceServerKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_recover_confirm_title),
        text = { ReplaceSteps(state) },
        confirmLabel = stringResource(Res.string.recovery_key_recover_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Says what replacing the registered recovery key changes before anything is sent. */
@Composable
fun ReplaceRecoveryKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(Res.string.recovery_key_replace_key_confirm_title),
        text = { Text(stringResource(Res.string.recovery_key_replace_key_confirm_body, state.serverName, state.handle)) },
        confirmLabel = stringResource(Res.string.recovery_key_replace_key_confirm_action),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: @Composable () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_cancel)) } },
    )
}

@Composable
private fun Explanation(
    handle: String,
    serverName: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(Res.string.recovery_key_intro, handle, serverName), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.recovery_key_what_it_does, serverName, handle), style = MaterialTheme.typography.bodyLarge)
        SupportingText(stringResource(Res.string.recovery_key_from_phrase, serverName))
        SupportingText(stringResource(Res.string.recovery_key_limits, serverName))
    }
}

@Composable
private fun SupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    )
}

@Composable
private fun ProblemWithRetry(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.recovery_key_try_again)) }
    }
}

@Composable
private fun phraseProblemMessage(problem: PhraseProblem): String =
    when (problem) {
        PhraseProblem.UNREADABLE -> stringResource(Res.string.recovery_key_phrase_unreadable)
        PhraseProblem.CANNOT_DERIVE -> stringResource(Res.string.recovery_key_phrase_cannot_derive)
        PhraseProblem.CONFIRMATION_UNREADABLE -> stringResource(Res.string.recovery_key_phrase_confirmation_unreadable)
    }

@Composable
private fun SetupSection(
    state: RecoveryKeyUiState.Ready,
    destinations: RecoveryKeyDestinations,
) {
    SettingsSection(
        title = stringResource(Res.string.recovery_key_setup_section),
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        SettingsNavigationItem(
            title = stringResource(Res.string.recovery_key_phrase_step_title),
            description =
                stringResource(
                    when (state.phrase) {
                        PhraseStatus.CONFIRMED -> Res.string.recovery_key_phrase_confirmed
                        PhraseStatus.NOT_CONFIRMED -> Res.string.recovery_key_phrase_not_confirmed
                        PhraseStatus.MISSING -> Res.string.recovery_key_phrase_missing
                    },
                ),
            icon = { PhraseIcon(state.phrase) },
            onClick = destinations.onRecoveryPhrase,
        )
    }
}

@Composable
private fun PhraseIcon(phrase: PhraseStatus) {
    when (phrase) {
        PhraseStatus.CONFIRMED -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        PhraseStatus.NOT_CONFIRMED -> Icon(Icons.Outlined.Password, contentDescription = null)
        PhraseStatus.MISSING -> Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TurnOnAction(
    state: RecoveryKeyUiState.Ready,
    onTurnOn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SupportingText(stringResource(Res.string.recovery_key_warning, state.handle))
        Button(onClick = onTurnOn, enabled = state.canTurnOn, modifier = Modifier.fillMaxWidth()) {
            WorkingLabel(working = state.action == RecoveryKeyAction.TURNING_ON, label = stringResource(Res.string.recovery_key_turn_on))
        }
        if (state.phrase != PhraseStatus.CONFIRMED) {
            SupportingText(stringResource(Res.string.recovery_key_turn_on_needs_phrase))
        }
    }
}

@Composable
private fun StatusSection(state: RecoveryKeyUiState.Ready) {
    SettingsSection(
        title = stringResource(Res.string.recovery_key_status_section),
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        when (state.key) {
            KeyStatus.ON ->
                SimpleSettingsItem(
                    title = stringResource(Res.string.recovery_key_on_title),
                    description = stringResource(Res.string.recovery_key_on_body, state.serverName, state.handle),
                    leadingContent = {
                        Icon(
                            Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    action = {},
                )
            KeyStatus.ON_FROM_OTHER_PHRASE ->
                SimpleSettingsItem(
                    title = stringResource(Res.string.recovery_key_other_phrase_title),
                    description = stringResource(Res.string.recovery_key_other_phrase_body, state.handle),
                    leadingContent = {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    action = {},
                )
            KeyStatus.ON_WITHOUT_PHRASE ->
                SimpleSettingsItem(
                    title = stringResource(Res.string.recovery_key_without_phrase_title),
                    description = stringResource(Res.string.recovery_key_without_phrase_body),
                    leadingContent = { Icon(Icons.Outlined.PhonelinkLock, contentDescription = null) },
                    action = {},
                )
            KeyStatus.OFF -> Unit
        }
    }
}

@Composable
private fun ReplaceServerKeySection(
    state: RecoveryKeyUiState.Ready,
    onReplace: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(Res.string.recovery_key_recover_section), style = MaterialTheme.typography.titleMedium)
        SupportingText(stringResource(Res.string.recovery_key_recover_body, state.serverName, state.handle))
        OutlinedButton(onClick = onReplace, enabled = state.canReplaceServerKey, modifier = Modifier.fillMaxWidth()) {
            WorkingLabel(
                working = state.action == RecoveryKeyAction.REPLACING_SERVER_KEY,
                label = stringResource(Res.string.recovery_key_recover_action),
            )
        }
    }
}

@Composable
private fun ReplaceRecoveryKeySection(
    state: RecoveryKeyUiState.Ready,
    destinations: RecoveryKeyDestinations,
    onReplace: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(Res.string.recovery_key_replace_key_section), style = MaterialTheme.typography.titleMedium)
        SupportingText(stringResource(Res.string.recovery_key_replace_key_body, state.handle, state.serverName))
        if (state.phrase != PhraseStatus.CONFIRMED) {
            SettingsSection(title = stringResource(Res.string.recovery_key_setup_section)) {
                SettingsNavigationItem(
                    title = stringResource(Res.string.recovery_key_phrase_step_title),
                    description = stringResource(Res.string.recovery_key_replace_key_needs_phrase),
                    icon = { PhraseIcon(state.phrase) },
                    onClick = destinations.onRecoveryPhrase,
                )
            }
        }
        OutlinedButton(onClick = onReplace, enabled = state.canReplaceRecoveryKey, modifier = Modifier.fillMaxWidth()) {
            WorkingLabel(
                working = state.action == RecoveryKeyAction.REPLACING_RECOVERY_KEY,
                label = stringResource(Res.string.recovery_key_replace_key_action),
            )
        }
    }
}

@Composable
private fun ReplaceSteps(state: RecoveryKeyUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.recovery_key_recover_confirm_intro))
        listOf(
            stringResource(Res.string.recovery_key_recover_confirm_step_check),
            stringResource(Res.string.recovery_key_recover_confirm_step_passkey),
            stringResource(Res.string.recovery_key_recover_confirm_step_replace, state.serverName, state.handle),
            stringResource(Res.string.recovery_key_recover_confirm_step_rank),
            stringResource(Res.string.recovery_key_recover_confirm_step_unchanged),
        ).forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("•")
                Text(step)
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.lg))
    }
}

@Composable
private fun ErrorNotice(
    state: RecoveryKeyUiState.Ready,
    failure: IdentityRecoveryFailure,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.sm, bottom = Spacing.xs)) {
            Text(errorMessage(state, failure), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.sm))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.recovery_key_dismiss))
            }
        }
    }
}

@Composable
private fun errorMessage(
    state: RecoveryKeyUiState.Ready,
    failure: IdentityRecoveryFailure,
): String =
    when (failure) {
        IdentityRecoveryFailure.NOT_AVAILABLE -> stringResource(Res.string.recovery_key_error_not_available, state.serverName)
        IdentityRecoveryFailure.ALREADY_SET -> stringResource(Res.string.recovery_key_error_already_set, state.handle)
        IdentityRecoveryFailure.NOT_SET -> stringResource(Res.string.recovery_key_error_not_set, state.handle)
        IdentityRecoveryFailure.KEY_REMOVED -> stringResource(Res.string.recovery_key_error_key_removed, state.handle, state.serverName)
        IdentityRecoveryFailure.PHRASE_MISMATCH -> stringResource(Res.string.recovery_key_error_phrase_mismatch, state.handle)
        IdentityRecoveryFailure.SIGNATURE_REJECTED -> stringResource(Res.string.recovery_key_error_signature_rejected, state.serverName)
        IdentityRecoveryFailure.UNEXPECTED_CHANGE -> stringResource(Res.string.recovery_key_error_unexpected_change, state.serverName)
        IdentityRecoveryFailure.SERVER_KEY_MISSING ->
            stringResource(Res.string.recovery_key_error_server_key_missing, state.serverName, state.handle)
        IdentityRecoveryFailure.IDENTITY_CHANGED -> stringResource(Res.string.recovery_key_error_identity_changed, state.handle)
        IdentityRecoveryFailure.SIGNED_OUT -> stringResource(Res.string.recovery_key_error_signed_out)
        IdentityRecoveryFailure.UNREACHABLE -> stringResource(Res.string.recovery_key_error_unreachable, state.serverName)
        IdentityRecoveryFailure.DEVICE_ERROR -> stringResource(Res.string.recovery_key_error_device)
        IdentityRecoveryFailure.REFUSED -> stringResource(Res.string.recovery_key_error_refused, state.serverName)
        IdentityRecoveryFailure.STEP_UP_CANCELLED -> stringResource(Res.string.recovery_key_error_step_up_cancelled)
        IdentityRecoveryFailure.STEP_UP_FAILED -> stringResource(Res.string.recovery_key_error_step_up_failed)
        IdentityRecoveryFailure.STEP_UP_UNAVAILABLE -> stringResource(Res.string.recovery_key_error_step_up_unavailable)
        IdentityRecoveryFailure.PASSKEY_REQUIRED -> stringResource(Res.string.recovery_key_error_passkey_required)
    }

@Composable
private fun WorkingLabel(
    working: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label)
    }
}

@Preview
@Composable
private fun RecoveryKeySetupPreview() {
    RecoveryKeyContent(
        state =
            RecoveryKeyUiState.Ready(
                handle = "alice.logdate.app",
                serverName = "LogDate Cloud",
                phrase = PhraseStatus.CONFIRMED,
                key = KeyStatus.OFF,
            ),
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}),
        actions = RecoveryKeyActions(),
    )
}

@Preview
@Composable
private fun RecoveryKeyOnPreview() {
    RecoveryKeyContent(
        state =
            RecoveryKeyUiState.Ready(
                handle = "alice.logdate.app",
                serverName = "LogDate Cloud",
                phrase = PhraseStatus.CONFIRMED,
                key = KeyStatus.ON,
            ),
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}),
        actions = RecoveryKeyActions(),
    )
}
