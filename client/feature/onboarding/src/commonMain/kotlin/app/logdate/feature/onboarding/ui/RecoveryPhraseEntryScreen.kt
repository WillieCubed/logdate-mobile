@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.Napier
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val RecoveryPhraseWordPattern = Regex("^[a-z]+$")

@Composable
fun RecoveryPhraseEntryScreen(
    onRecovered: () -> Unit,
    onError: (String) -> Unit = {},
) {
    var phraseWords by remember { mutableStateOf(List(12) { "" }) }
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessageRes by remember { mutableStateOf<StringResource?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.enter_recovery_phrase),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = stringResource(Res.string.enter_your_12_word_recovery_phrase_to_restore_your_encryption_keys_and_decrypt_your_data),
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(phraseWords) { index, word ->
                OutlinedTextField(
                    value = word,
                    onValueChange = { newValue ->
                        phraseWords =
                            phraseWords.toMutableList().apply {
                                this[index] = newValue.lowercase().trim()
                            }
                        errorMessageRes = null
                    },
                    label = { Text((index + 1).toString()) },
                    singleLine = true,
                    enabled = !isRecovering,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        errorMessageRes?.let { messageRes ->
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (isRecovering) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(Res.string.deriving_encryption_keys))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val normalizedWords = phraseWords.map { it.trim().lowercase() }
                val validationError = validateRecoveryPhrase(normalizedWords)
                if (validationError != null) {
                    errorMessageRes = validationError
                    return@Button
                }

                isRecovering = true
                errorMessageRes = null
                phraseWords = normalizedWords
                // TODO: Call IdentityKeyManager.recoverIdentity(phraseWords)
                Napier.d("Attempting recovery with phrase: ${normalizedWords.take(3).map { "***" }}")

                runCatching(onRecovered)
                    .onFailure { throwable ->
                        val reason =
                            throwable.message?.takeIf { it.isNotBlank() }
                                ?: "Recovery flow failed"
                        Napier.e("Recovery phrase flow failed: $reason", throwable)
                        errorMessageRes = Res.string.recovery_error_unexpected
                        onError(reason)
                    }

                isRecovering = false
            },
            enabled = !isRecovering,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.recover_account))
        }
    }
}

private fun validateRecoveryPhrase(words: List<String>): StringResource? {
    if (words.any { it.isBlank() }) {
        return Res.string.recovery_error_missing_words
    }
    if (words.any { !RecoveryPhraseWordPattern.matches(it) }) {
        return Res.string.recovery_error_invalid_word_format
    }
    return null
}
