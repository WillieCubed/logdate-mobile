@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.step.StepHeroIcon
import app.logdate.ui.step.StepScaffold
import app.logdate.ui.theme.LogDateTheme
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
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
    StepScaffold(
        title = stringResource(Res.string.now_lets_import_your_memories),
        onBack = onBack,
        modifier = modifier.testTag(MEMORIES_IMPORT_INFO_ROOT_TAG),
        supportingText =
            stringResource(Res.string.onboarding_import_memories_description) + "\n\n" +
                stringResource(Res.string.onboarding_import_photos_description),
        hero = { StepHeroIcon(Icons.Rounded.PhotoLibrary) },
        headerAlignment = Alignment.CenterHorizontally,
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().testTag(MEMORIES_IMPORT_INFO_CONTINUE_TAG),
            ) {
                Text(stringResource(UiRes.string.common_continue))
            }
        },
    )
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
