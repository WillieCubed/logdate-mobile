@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_cadence_body
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_cadence_heading
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_got_it
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_share_body
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_share_heading
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_strictness_body
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_strictness_heading
import logdate.client.feature.rewind.generated.resources.first_rewind_onboarding_title
import org.jetbrains.compose.resources.stringResource

/**
 * One-time bottom sheet shown the first time the user opens the Rewind tab. Explains
 * the weekly cadence, how sharing works, and points at the photo-strictness toggle so
 * users know they can tune it.
 *
 * The host owns the "should we show this?" decision — typically gated on
 * `LogdatePreferencesDataSource.hasSeenRewindOnboarding()`. Tapping the Got it button
 * or dismissing the sheet should set that flag to true so this never appears again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRewindOnboardingSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        FirstRewindOnboardingSheetContent(onDismiss = onDismiss)
    }
}

/**
 * Sheet content extracted so it can be rendered in screenshot scenes and previews
 * without the platform-specific [ModalBottomSheet] wrapper.
 */
@Composable
fun FirstRewindOnboardingSheetContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(Res.string.first_rewind_onboarding_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OnboardingSection(
            heading = stringResource(Res.string.first_rewind_onboarding_cadence_heading),
            body = stringResource(Res.string.first_rewind_onboarding_cadence_body),
        )
        OnboardingSection(
            heading = stringResource(Res.string.first_rewind_onboarding_share_heading),
            body = stringResource(Res.string.first_rewind_onboarding_share_body),
        )
        OnboardingSection(
            heading = stringResource(Res.string.first_rewind_onboarding_strictness_heading),
            body = stringResource(Res.string.first_rewind_onboarding_strictness_body),
        )

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onDismiss,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(Res.string.first_rewind_onboarding_got_it))
        }
    }
}

@Composable
private fun OnboardingSection(
    heading: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
