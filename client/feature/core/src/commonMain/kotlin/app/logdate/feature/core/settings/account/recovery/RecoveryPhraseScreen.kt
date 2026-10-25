@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.account.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.platform.plainTextClipEntry
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.recovery_phrase_copy
import logdate.client.feature.core.generated.resources.recovery_phrase_enter
import logdate.client.feature.core.generated.resources.recovery_phrase_hide
import logdate.client.feature.core.generated.resources.recovery_phrase_intro
import logdate.client.feature.core.generated.resources.recovery_phrase_missing_body
import logdate.client.feature.core.generated.resources.recovery_phrase_missing_title
import logdate.client.feature.core.generated.resources.recovery_phrase_not_confirmed
import logdate.client.feature.core.generated.resources.recovery_phrase_prompt_subtitle
import logdate.client.feature.core.generated.resources.recovery_phrase_prompt_title
import logdate.client.feature.core.generated.resources.recovery_phrase_saved_action
import logdate.client.feature.core.generated.resources.recovery_phrase_show
import logdate.client.feature.core.generated.resources.recovery_phrase_title
import logdate.client.feature.core.generated.resources.recovery_phrase_unreadable
import logdate.client.feature.core.generated.resources.recovery_phrase_verified
import logdate.client.feature.core.generated.resources.recovery_phrase_verify_failed
import logdate.client.feature.core.generated.resources.recovery_phrase_write_down
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the 12-word recovery phrase behind the device's screen lock and, where it is offered,
 * whether the confirmed phrase can also move the account.
 */
@Composable
fun RecoveryPhraseScreen(
    onBack: () -> Unit,
    onEnterPhrase: () -> Unit,
    viewModel: RecoveryPhraseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountRecovery by viewModel.accountRecovery.collectAsStateWithLifecycle()
    val prompt =
        RevealPrompt(
            title = stringResource(Res.string.recovery_phrase_prompt_title),
            subtitle = stringResource(Res.string.recovery_phrase_prompt_subtitle),
            description = null,
        )

    // Coming back from entering the phrase finds it; a revealed phrase stays shown through a rotation.
    LaunchedEffect(Unit) { viewModel.check() }

    RecoveryPhraseContent(
        state = state,
        onBack = onBack,
        onReveal = { viewModel.reveal(prompt) },
        onHide = viewModel::hide,
        onEnterPhrase = onEnterPhrase,
        onConfirmSaved = viewModel::confirmSaved,
        accountRecovery = accountRecovery,
        onFinishAccountRecovery = viewModel::finishAccountRecoverySetup,
        onRetryAccountRecovery = viewModel::checkAccountRecovery,
    )
}

@Composable
fun RecoveryPhraseContent(
    state: RecoveryPhraseUiState,
    onBack: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onEnterPhrase: () -> Unit,
    onConfirmSaved: () -> Unit = {},
    accountRecovery: AccountRecoveryUiState? = null,
    onFinishAccountRecovery: () -> Unit = {},
    onRetryAccountRecovery: () -> Unit = {},
) {
    SettingsScaffold(title = stringResource(Res.string.recovery_phrase_title), onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                when (state) {
                    RecoveryPhraseUiState.Checking,
                    RecoveryPhraseUiState.Confirming,
                    RecoveryPhraseUiState.Verifying,
                    ->
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    RecoveryPhraseUiState.NotOnThisDevice -> {
                        Text(stringResource(Res.string.recovery_phrase_missing_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.recovery_phrase_missing_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onEnterPhrase) { Text(stringResource(Res.string.recovery_phrase_enter)) }
                    }

                    RecoveryPhraseUiState.Hidden -> {
                        Intro()
                        AccountRecovery(accountRecovery, onFinishAccountRecovery, onRetryAccountRecovery)
                        ShowButton(onReveal)
                    }

                    RecoveryPhraseUiState.Verified -> {
                        Intro()
                        DoneLine(stringResource(Res.string.recovery_phrase_verified))
                        AccountRecovery(accountRecovery, onFinishAccountRecovery, onRetryAccountRecovery)
                        ShowButton(onReveal)
                    }

                    RecoveryPhraseUiState.VerificationFailed -> {
                        Text(
                            stringResource(Res.string.recovery_phrase_verify_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        ShowButton(onReveal)
                    }

                    is RecoveryPhraseUiState.Failed -> {
                        Intro()
                        Text(
                            stringResource(
                                when (state.reason) {
                                    RevealFailure.NOT_CONFIRMED -> Res.string.recovery_phrase_not_confirmed
                                    RevealFailure.UNREADABLE -> Res.string.recovery_phrase_unreadable
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        ShowButton(onReveal)
                    }

                    is RecoveryPhraseUiState.Revealed -> {
                        Intro()
                        PhraseGrid(state.words)
                        if (!state.confirmed) {
                            Text(stringResource(Res.string.recovery_phrase_write_down), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onConfirmSaved, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.recovery_phrase_saved_action))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            CopyPhraseButton(state.words)
                            OutlinedButton(onClick = onHide) {
                                Icon(Icons.Outlined.VisibilityOff, contentDescription = null)
                                Text(stringResource(Res.string.recovery_phrase_hide), modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRecovery(
    state: AccountRecoveryUiState?,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    state?.let { AccountRecoveryNotice(it, onFinish = onFinish, onRetry = onRetry) }
}

@Composable
private fun CopyPhraseButton(words: List<String>) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(plainTextClipEntry(words.joinToString(" "), sensitive = true)) } }) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
        Text(stringResource(Res.string.recovery_phrase_copy), modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Composable
private fun Intro() {
    Text(
        stringResource(Res.string.recovery_phrase_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShowButton(onReveal: () -> Unit) {
    FilledTonalButton(onClick = onReveal) {
        Icon(Icons.Outlined.Visibility, contentDescription = null)
        Text(stringResource(Res.string.recovery_phrase_show), modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** The words in two columns, numbered in reading order down each column. */
@Composable
private fun PhraseGrid(words: List<String>) {
    val half = (words.size + 1) / 2
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            listOf(words.take(half), words.drop(half)).forEachIndexed { column, columnWords ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    columnWords.forEachIndexed { row, word ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (column * half + row + 1).toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = Spacing.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecoveryPhraseRevealedPreview() {
    RecoveryPhraseContent(
        state = RecoveryPhraseUiState.Revealed(List(12) { "word${it + 1}" }),
        onBack = {},
        onReveal = {},
        onHide = {},
        onEnterPhrase = {},
    )
}
