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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Password
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
import logdate.client.feature.core.generated.resources.recovery_key_enter_phrase
import logdate.client.feature.core.generated.resources.recovery_key_error_already_set
import logdate.client.feature.core.generated.resources.recovery_key_error_device
import logdate.client.feature.core.generated.resources.recovery_key_error_identity_changed
import logdate.client.feature.core.generated.resources.recovery_key_error_key_removed
import logdate.client.feature.core.generated.resources.recovery_key_error_not_available
import logdate.client.feature.core.generated.resources.recovery_key_error_not_set
import logdate.client.feature.core.generated.resources.recovery_key_error_phrase_mismatch
import logdate.client.feature.core.generated.resources.recovery_key_error_refused
import logdate.client.feature.core.generated.resources.recovery_key_error_signed_out
import logdate.client.feature.core.generated.resources.recovery_key_error_unreachable
import logdate.client.feature.core.generated.resources.recovery_key_from_phrase
import logdate.client.feature.core.generated.resources.recovery_key_intro
import logdate.client.feature.core.generated.resources.recovery_key_load_failed
import logdate.client.feature.core.generated.resources.recovery_key_on_body
import logdate.client.feature.core.generated.resources.recovery_key_on_title
import logdate.client.feature.core.generated.resources.recovery_key_other_phrase_body
import logdate.client.feature.core.generated.resources.recovery_key_other_phrase_title
import logdate.client.feature.core.generated.resources.recovery_key_phrase_confirmed
import logdate.client.feature.core.generated.resources.recovery_key_phrase_missing
import logdate.client.feature.core.generated.resources.recovery_key_phrase_not_confirmed
import logdate.client.feature.core.generated.resources.recovery_key_phrase_step_title
import logdate.client.feature.core.generated.resources.recovery_key_recover_action
import logdate.client.feature.core.generated.resources.recovery_key_recover_body
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_action
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_intro
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_rank
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_replace
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_sign
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_step_unchanged
import logdate.client.feature.core.generated.resources.recovery_key_recover_confirm_title
import logdate.client.feature.core.generated.resources.recovery_key_recover_section
import logdate.client.feature.core.generated.resources.recovery_key_recovered
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
import logdate.client.feature.core.generated.resources.recovery_key_without_phrase_body
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

/** Where the Recovery key screen leads. */
data class RecoveryKeyDestinations(
    val onBack: () -> Unit,
    /** Opens the Recovery phrase page, where the person reveals, saves and confirms their phrase. */
    val onRecoveryPhrase: () -> Unit,
    /** Opens phrase entry, for a device that does not have the phrase. */
    val onEnterPhrase: () -> Unit,
)

/** Sets up the person's recovery key and uses it to replace the server's key. */
@Composable
fun RecoveryKeyScreen(
    destinations: RecoveryKeyDestinations,
    viewModel: RecoveryKeyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Coming back from saving or entering the phrase finds it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    RecoveryKeyContent(
        state = state,
        destinations = destinations,
        onTurnOn = viewModel::turnOn,
        onReplaceServerKey = viewModel::replaceServerKey,
        onRetry = viewModel::refresh,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
fun RecoveryKeyContent(
    state: RecoveryKeyUiState,
    destinations: RecoveryKeyDestinations,
    onTurnOn: () -> Unit,
    onReplaceServerKey: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    var confirmTurnOn by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(Res.string.recovery_key_title), onBack = destinations.onBack) {
        when (state) {
            RecoveryKeyUiState.Loading ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

            is RecoveryKeyUiState.LoadFailed ->
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Text(
                            stringResource(Res.string.recovery_key_load_failed, state.serverName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.recovery_key_try_again)) }
                    }
                }

            is RecoveryKeyUiState.Ready -> {
                item { Explanation(state) }
                state.error?.let { failure -> item { ErrorNotice(state, failure, onDismissError) } }
                if (state.replacedServerKey) {
                    item { ReplacedNotice(state) }
                }
                if (state.key == KeyStatus.OFF) {
                    item { SetupSection(state, destinations) }
                    item { TurnOnAction(state, onTurnOn = { confirmTurnOn = true }) }
                } else {
                    item { StatusSection(state, destinations) }
                    if (state.key == KeyStatus.ON) {
                        item { TakeBackControlSection(state, onReplace = { confirmReplace = true }) }
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
                onTurnOn()
            },
            onDismiss = { confirmTurnOn = false },
        )
    }
    if (confirmReplace) {
        ReplaceServerKeyDialog(
            state = ready,
            onConfirm = {
                confirmReplace = false
                onReplaceServerKey()
            },
            onDismiss = { confirmReplace = false },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.recovery_key_turn_on_confirm_title)) },
        text = { Text(stringResource(Res.string.recovery_key_turn_on_confirm_body, state.serverName, state.handle)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.recovery_key_turn_on_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_cancel)) } },
    )
}

/** Lists what replacing the server's key will do before anything is sent. */
@Composable
fun ReplaceServerKeyDialog(
    state: RecoveryKeyUiState.Ready,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.recovery_key_recover_confirm_title)) },
        text = { ReplaceSteps(state) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.recovery_key_recover_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiRes.string.common_cancel)) } },
    )
}

@Composable
private fun Explanation(state: RecoveryKeyUiState.Ready) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            stringResource(Res.string.recovery_key_intro, state.handle, state.serverName),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(Res.string.recovery_key_from_phrase, state.serverName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            icon = {
                when (state.phrase) {
                    PhraseStatus.CONFIRMED ->
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    PhraseStatus.NOT_CONFIRMED -> Icon(Icons.Outlined.Password, contentDescription = null)
                    PhraseStatus.MISSING ->
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            },
            onClick = if (state.phrase == PhraseStatus.MISSING) destinations.onEnterPhrase else destinations.onRecoveryPhrase,
        )
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
        Text(
            stringResource(Res.string.recovery_key_warning, state.handle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onTurnOn, enabled = state.canTurnOn, modifier = Modifier.fillMaxWidth()) {
            WorkingLabel(
                working = state.action == RecoveryKeyAction.TURNING_ON,
                label = stringResource(Res.string.recovery_key_turn_on),
            )
        }
        if (state.phrase != PhraseStatus.CONFIRMED) {
            Text(
                stringResource(Res.string.recovery_key_turn_on_needs_phrase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusSection(
    state: RecoveryKeyUiState.Ready,
    destinations: RecoveryKeyDestinations,
) {
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
            // Entering a phrase here would replace the one that unlocks this device's journal, so no entry is offered.
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
            KeyStatus.ON_WITHOUT_PHRASE -> {
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
                SettingsNavigationItem(
                    title = stringResource(Res.string.recovery_key_enter_phrase),
                    description = stringResource(Res.string.recovery_key_without_phrase_body),
                    icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    onClick = destinations.onEnterPhrase,
                )
            }
            KeyStatus.OFF -> Unit
        }
    }
}

@Composable
private fun TakeBackControlSection(
    state: RecoveryKeyUiState.Ready,
    onReplace: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(Res.string.recovery_key_recover_section), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.recovery_key_recover_body, state.serverName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onReplace, enabled = state.canReplaceServerKey, modifier = Modifier.fillMaxWidth()) {
            WorkingLabel(
                working = state.action == RecoveryKeyAction.REPLACING_SERVER_KEY,
                label = stringResource(Res.string.recovery_key_recover_action),
            )
        }
    }
}

@Composable
private fun ReplaceSteps(state: RecoveryKeyUiState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.recovery_key_recover_confirm_intro))
        listOf(
            stringResource(Res.string.recovery_key_recover_confirm_step_sign),
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
private fun ReplacedNotice(state: RecoveryKeyUiState.Ready) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    ) {
        Text(
            stringResource(Res.string.recovery_key_recovered, state.serverName, state.handle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Spacing.lg),
        )
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
        IdentityRecoveryFailure.KEY_REMOVED -> stringResource(Res.string.recovery_key_error_key_removed, state.handle)
        IdentityRecoveryFailure.PHRASE_MISMATCH -> stringResource(Res.string.recovery_key_error_phrase_mismatch, state.handle)
        IdentityRecoveryFailure.IDENTITY_CHANGED -> stringResource(Res.string.recovery_key_error_identity_changed, state.handle)
        IdentityRecoveryFailure.SIGNED_OUT -> stringResource(Res.string.recovery_key_error_signed_out)
        IdentityRecoveryFailure.UNREACHABLE -> stringResource(Res.string.recovery_key_error_unreachable, state.serverName)
        IdentityRecoveryFailure.DEVICE_ERROR -> stringResource(Res.string.recovery_key_error_device)
        IdentityRecoveryFailure.REFUSED -> stringResource(Res.string.recovery_key_error_refused, state.serverName)
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
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}, onEnterPhrase = {}),
        onTurnOn = {},
        onReplaceServerKey = {},
        onRetry = {},
        onDismissError = {},
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
        destinations = RecoveryKeyDestinations(onBack = {}, onRecoveryPhrase = {}, onEnterPhrase = {}),
        onTurnOn = {},
        onReplaceServerKey = {},
        onRetry = {},
        onDismissError = {},
    )
}
