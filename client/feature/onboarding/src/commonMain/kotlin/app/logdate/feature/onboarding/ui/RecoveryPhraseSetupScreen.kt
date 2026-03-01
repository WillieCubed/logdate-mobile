package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecoveryPhraseSetupScreen(
    onPhraseContinue: (List<String>) -> Unit
) {
    var phase by remember { mutableStateOf<SetupPhase>(SetupPhase.Display(emptyList())) }

    when (val currentPhase = phase) {
        is SetupPhase.Display -> {
            RecoveryPhraseDisplayContent(
                words = currentPhase.words,
                onContinue = { phase = SetupPhase.Verify(currentPhase.words) }
            )
        }

        is SetupPhase.Verify -> {
            RecoveryPhraseVerificationContent(
                expectedWords = currentPhase.words,
                onVerified = { onPhraseContinue(currentPhase.words) }
            )
        }
    }
}

@Composable
private fun RecoveryPhraseDisplayContent(
    words: List<String>,
    onContinue: () -> Unit
) {
    var userConfirmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔑 Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Write these 12 words on paper and store them safely. You'll need them to recover your data if you lose your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(words) { index, word ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        WarningCard(icon = "⚠️", text = "Never share your recovery phrase with anyone")
        WarningCard(icon = "📝", text = "Write it down on paper - don't save digitally")
        WarningCard(icon = "🔒", text = "Store it in a safe place")

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = userConfirmed,
                onCheckedChange = { userConfirmed = it }
            )
            Text(
                text = "I have written down my recovery phrase",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onContinue,
            enabled = userConfirmed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun RecoveryPhraseVerificationContent(
    expectedWords: List<String>,
    onVerified: () -> Unit
) {
    val wordsToVerify = remember {
        expectedWords.indices.shuffled().take(3).sorted()
    }

    var userInputs by remember {
        mutableStateOf(wordsToVerify.associateWith { "" })
    }

    val allCorrect = wordsToVerify.all { index ->
        userInputs[index]?.lowercase() == expectedWords[index].lowercase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Verify Your Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Enter the following words from your recovery phrase to confirm you've written it down correctly.",
            style = MaterialTheme.typography.bodyMedium
        )

        wordsToVerify.forEach { index ->
            OutlinedTextField(
                value = userInputs[index] ?: "",
                onValueChange = { userInputs = userInputs + (index to it) },
                label = { Text("Word #${index + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onVerified,
            enabled = allCorrect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify & Continue")
        }
    }
}

@Composable
private fun WarningCard(icon: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

sealed class SetupPhase {
    data class Display(val words: List<String>) : SetupPhase()
    data class Verify(val words: List<String>) : SetupPhase()
}
