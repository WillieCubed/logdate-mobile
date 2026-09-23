@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.sync.RecoverIdentityUseCase
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_description
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_error
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_submit
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_title
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_word_count_error
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class RecoveryPhraseEntryViewModel(
    private val recoverIdentityUseCase: RecoverIdentityUseCase,
) : ViewModel() {
    fun submit(
        words: List<String>,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = recoverIdentityUseCase(words)
            result.onFailure { Napier.e("Could not recover identity from Settings", it) }
            onResult(result)
        }
    }
}

/**
 * Lets a signed-in user enter their recovery phrase again from Settings -- the recovery step in
 * onboarding can be skipped, and a device can also lose its key later (a Keystore reset, a
 * restore). Both leave backup paused with nowhere else to fix it from.
 */
@Composable
fun RecoveryPhraseEntrySettingsScreen(
    onBack: () -> Unit,
    onRecovered: () -> Unit,
    viewModel: RecoveryPhraseEntryViewModel = koinViewModel(),
) {
    var phraseWords by remember { mutableStateOf(List(12) { "" }) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val wordCountError = stringResource(Res.string.recovery_phrase_entry_settings_word_count_error)
    val genericError = stringResource(Res.string.recovery_phrase_entry_settings_error)

    SettingsScaffold(title = stringResource(Res.string.recovery_phrase_entry_settings_title), onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.recovery_phrase_entry_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    phraseWords.chunked(2).forEachIndexed { rowIndex, rowWords ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            rowWords.forEachIndexed { colIndex, word ->
                                val index = rowIndex * 2 + colIndex
                                OutlinedTextField(
                                    value = word,
                                    onValueChange = { newValue ->
                                        phraseWords =
                                            phraseWords.toMutableList().apply {
                                                this[index] = newValue.lowercase().trim()
                                            }
                                        errorMessage = null
                                    },
                                    label = { Text((index + 1).toString()) },
                                    singleLine = true,
                                    enabled = !isSubmitting,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                errorMessage?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }

                if (isSubmitting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        CircularProgressIndicator()
                    }
                }

                Button(
                    onClick = {
                        val normalized = phraseWords.map { it.trim().lowercase() }
                        if (normalized.any { it.isBlank() }) {
                            errorMessage = wordCountError
                            return@Button
                        }
                        isSubmitting = true
                        errorMessage = null
                        viewModel.submit(normalized) { result ->
                            isSubmitting = false
                            result
                                .onSuccess { onRecovered() }
                                .onFailure { errorMessage = it.message?.takeIf { m -> m.isNotBlank() } ?: genericError }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.recovery_phrase_entry_settings_submit))
                }
            }
        }
    }
}
