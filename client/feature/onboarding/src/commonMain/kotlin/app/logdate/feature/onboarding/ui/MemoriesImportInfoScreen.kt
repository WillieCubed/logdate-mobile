@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

const val MEMORIES_IMPORT_INFO_ROOT_TAG = "onboarding_memory_import_root"
const val MEMORIES_IMPORT_INFO_CONTINUE_TAG = "onboarding_memory_import_continue"

/**
 * Screen that explains the memories import feature during onboarding.
 */
@Composable
fun MemoriesImportInfoScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .testTag(MEMORIES_IMPORT_INFO_ROOT_TAG)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Back arrow row
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(UiRes.string.common_back),
                        )
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 444.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // Inline headline per Figma
                    Text(
                        text = stringResource(Res.string.now_lets_import_your_memories),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )

                    Text(
                        text = stringResource(Res.string.onboarding_import_memories_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Text(
                        text = stringResource(Res.string.onboarding_import_photos_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    // Button at end of scroll content so user reads the explanation first
                    Button(
                        onClick = onContinue,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xl, bottom = Spacing.lg)
                                .testTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG),
                    ) {
                        Text(stringResource(UiRes.string.common_continue))
                    }
                }
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
