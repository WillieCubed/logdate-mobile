@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
                title = { Text(stringResource(Res.string.memories_import)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(UiRes.string.common_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .testTag(MEMORIES_IMPORT_INFO_ROOT_TAG)
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(Spacing.lg),
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.now_lets_import_your_memories),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Start,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = stringResource(Res.string.by_importing_your_memories_youll_be_able_to_see_memories_from_your_past),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Text(
                        text =
                            stringResource(
                                Res.string.weve_taken_the_effort_of_finding_some_photos_from_your_past_you_can_choose_which_ones_get_backed_up,
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Button(
                onClick = onContinue,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG),
            ) {
                Text(stringResource(UiRes.string.common_continue))
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
