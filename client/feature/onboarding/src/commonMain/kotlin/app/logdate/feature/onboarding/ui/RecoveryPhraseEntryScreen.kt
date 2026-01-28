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

@Composable
fun RecoveryPhraseEntryScreen(
    onRecovered: () -> Unit,
    onError: (String) -> Unit = {}
) {
    var phraseWords by remember { mutableStateOf(List(12) { "" }) }
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enter Recovery Phrase",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Enter your 12-word recovery phrase to restore your encryption keys and decrypt your data.",
            style = MaterialTheme.typography.bodyMedium
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(phraseWords) { index, word ->
                OutlinedTextField(
                    value = word,
                    onValueChange = { newValue ->
                        phraseWords = phraseWords.toMutableList().apply {
                            this[index] = newValue.lowercase().trim()
                        }
                    },
                    label = { Text("${index + 1}") },
                    singleLine = true,
                    enabled = !isRecovering,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "❌ ${errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (isRecovering) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Deriving encryption keys...")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (phraseWords.all { it.isNotBlank() }) {
                    isRecovering = true
                    errorMessage = null
                    // TODO: Call IdentityKeyManager.recoverIdentity(phraseWords)
                    Napier.d("Attempting recovery with phrase: ${phraseWords.take(3).map { "***" }}")
                    onRecovered()
                }
            },
            enabled = phraseWords.all { it.isNotBlank() } && !isRecovering,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Recover Account")
        }
    }
}
