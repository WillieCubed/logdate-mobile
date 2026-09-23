@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.logdate.ui.step.StepScaffold
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.action_onboarding_retry
import logdate.client.feature.onboarding.generated.resources.onboarding_error_save_completion
import logdate.client.feature.onboarding.generated.resources.onboarding_error_save_completion_title
import org.jetbrains.compose.resources.stringResource

const val ONBOARDING_SAVE_FAILED_TAG = "onboarding_save_failed"
const val ONBOARDING_SAVE_RETRY_TAG = "onboarding_save_retry"

/**
 * Shown by the terminal onboarding screens when saving that onboarding is complete fails, in
 * place of leaving onboarding.
 */
@Composable
internal fun OnboardingSaveFailedContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepScaffold(
        title = stringResource(Res.string.onboarding_error_save_completion_title),
        onBack = null,
        supportingText = stringResource(Res.string.onboarding_error_save_completion),
        modifier = modifier.testTag(ONBOARDING_SAVE_FAILED_TAG),
        actions = {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_SAVE_RETRY_TAG),
            ) {
                Text(stringResource(Res.string.action_onboarding_retry))
            }
        },
    )
}
