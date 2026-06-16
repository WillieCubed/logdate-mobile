@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports", "ktlint:standard:max-line-length")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
fun RecoveryPhraseSetupScreen(
    words: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onPhraseContinue: (List<String>) -> Unit,
) {
    var isVerifying by remember(words) { mutableStateOf(false) }

    when {
        isLoading -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(Res.string.deriving_encryption_keys))
            }
        }
        errorMessage != null -> {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.your_recovery_phrase),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.recovery_setup_retry))
                }
            }
        }
        !isVerifying -> {
            RecoveryPhraseDisplayContent(
                words = words,
                onContinue = { isVerifying = true },
            )
        }

        else -> {
            RecoveryPhraseVerificationContent(
                expectedWords = words,
                onVerified = { onPhraseContinue(words) },
            )
        }
    }
}

@Composable
private fun RecoveryPhraseDisplayContent(
    words: List<String>,
    onContinue: () -> Unit,
) {
    var userConfirmed by remember { mutableStateOf(false) }

    FoldableTabletopLayout(
        modifier = Modifier.fillMaxSize(),
        minPaneHeight = 260.dp,
        topPane = {
            RecoveryPhraseDisplayTopPane(
                words = words,
                modifier = Modifier.fillMaxSize(),
            )
        },
        bottomPane = {
            RecoveryPhraseDisplayBottomPane(
                userConfirmed = userConfirmed,
                onUserConfirmedChange = { userConfirmed = it },
                onContinue = onContinue,
                modifier = Modifier.fillMaxSize(),
            )
        },
        standardContent = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize(),
                minPaneWidth = 320.dp,
                startPane = {
                    RecoveryPhraseDisplayTopPane(
                        words = words,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                endPane = {
                    RecoveryPhraseDisplayBottomPane(
                        userConfirmed = userConfirmed,
                        onUserConfirmedChange = { userConfirmed = it },
                        onContinue = onContinue,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    RecoveryPhraseDisplayCompactContent(
                        words = words,
                        userConfirmed = userConfirmed,
                        onUserConfirmedChange = { userConfirmed = it },
                        onContinue = onContinue,
                    )
                },
            )
        },
    )
}

@Composable
private fun RecoveryPhraseDisplayTopPane(
    words: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.your_recovery_phrase),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text =
                stringResource(
                    Res.string.write_these_12_words_on_paper_and_store_them_safely_youll_need_them_to_recover_your_data_if_you_lose_your_device,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEachIndexed { index, word ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    Res.string.recovery_phrase_index,
                                    index + 1,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        WarningCard(
            icon = "⚠️",
            text = stringResource(Res.string.warning_recovery_phrase_never_share),
        )
        WarningCard(
            icon = "📝",
            text = stringResource(Res.string.warning_recovery_phrase_store_securely),
        )
        WarningCard(
            icon = "🔒",
            text = stringResource(Res.string.warning_recovery_phrase_store_safe),
        )
    }
}

@Composable
private fun RecoveryPhraseDisplayBottomPane(
    userConfirmed: Boolean,
    onUserConfirmedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = userConfirmed,
                onCheckedChange = onUserConfirmedChange,
            )
            Text(
                text = stringResource(Res.string.i_have_written_down_my_recovery_phrase),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onContinue,
            enabled = userConfirmed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(UiRes.string.common_continue))
        }
    }
}

@Composable
private fun RecoveryPhraseDisplayCompactContent(
    words: List<String>,
    userConfirmed: Boolean,
    onUserConfirmedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.your_recovery_phrase),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text =
                stringResource(
                    Res.string.write_these_12_words_on_paper_and_store_them_safely_youll_need_them_to_recover_your_data_if_you_lose_your_device,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEachIndexed { index, word ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    Res.string.recovery_phrase_index,
                                    index + 1,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        WarningCard(icon = "⚠️", text = stringResource(Res.string.warning_recovery_phrase_never_share))
        WarningCard(icon = "📝", text = stringResource(Res.string.warning_recovery_phrase_store_securely))
        WarningCard(icon = "🔒", text = stringResource(Res.string.warning_recovery_phrase_store_safe))
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = userConfirmed, onCheckedChange = onUserConfirmedChange)
            Text(
                text = stringResource(Res.string.i_have_written_down_my_recovery_phrase),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onContinue, enabled = userConfirmed, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(UiRes.string.common_continue))
        }
    }
}

@Composable
private fun RecoveryPhraseVerificationContent(
    expectedWords: List<String>,
    onVerified: () -> Unit,
) {
    val wordsToVerify =
        remember {
            expectedWords.indices
                .shuffled()
                .take(3)
                .sorted()
        }

    var userInputs by remember {
        mutableStateOf(wordsToVerify.associateWith { "" })
    }

    val allCorrect =
        wordsToVerify.all { index ->
            userInputs[index]?.lowercase() == expectedWords[index].lowercase()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.verify_your_recovery_phrase),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text =
                stringResource(
                    Res.string.enter_the_following_words_from_your_recovery_phrase_to_confirm_youve_written_it_down_correctly,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )

        wordsToVerify.forEach { index ->
            OutlinedTextField(
                value = userInputs[index] ?: "",
                onValueChange = { userInputs = userInputs + (index to it) },
                label = {
                    Text(
                        stringResource(
                            Res.string.word_number,
                            index + 1,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onVerified,
            enabled = allCorrect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.verify_and_continue))
        }
    }
}

@Composable
private fun WarningCard(
    icon: String,
    text: String,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
