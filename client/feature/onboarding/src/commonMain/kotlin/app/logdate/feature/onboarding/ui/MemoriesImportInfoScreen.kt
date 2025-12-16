package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Screen that explains the memories import feature during onboarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesImportInfoScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Memories Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = "Now let's import your memories.",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Start,
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = "By importing your memories, you'll be able to see memories from your past.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    
                    Text(
                        text = "We've taken the effort of finding some photos from your past. You can choose which ones get backed up.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

@Preview
@Composable
private fun MemoriesImportInfoScreenPreview() {
    LogDateTheme {
        MemoriesImportInfoScreen(
            onBack = {},
            onContinue = {},
        )
    }
}