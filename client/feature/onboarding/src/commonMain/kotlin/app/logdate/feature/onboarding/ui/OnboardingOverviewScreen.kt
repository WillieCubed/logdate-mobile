@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import logdate.client.ui.generated.resources.book_open
import logdate.client.ui.generated.resources.common_continue
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as coreRes

const val ONBOARDING_OVERVIEW_ROOT_TAG = "onboarding_overview_root"
const val ONBOARDING_OVERVIEW_CONTINUE_TAG = "onboarding_overview_continue"

@Composable
fun OnboardingOverviewScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold(
        title = stringResource(Res.string.onboarding_overview_title),
        onBack = onBack,
        modifier = Modifier.testTag(ONBOARDING_OVERVIEW_ROOT_TAG),
        hero = { StepHeroIcon(Icons.Rounded.AutoStories) },
        headerAlignment = Alignment.CenterHorizontally,
        actions = {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_OVERVIEW_CONTINUE_TAG),
            ) {
                Text(text = stringResource(coreRes.string.common_continue))
            }
        },
    ) {
        OverviewItem(
            title = stringResource(Res.string.onboarding_overview_card_log_title),
            description = stringResource(Res.string.onboarding_overview_card_log_description),
            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        )
        OverviewItem(
            title = stringResource(Res.string.onboarding_overview_card_journals_title),
            description = stringResource(Res.string.onboarding_overview_card_journals_description),
            icon = { Icon(painterResource(coreRes.drawable.book_open), contentDescription = null) },
        )
        OverviewItem(
            title = stringResource(Res.string.onboarding_overview_card_recaps_title),
            description = stringResource(Res.string.onboarding_overview_card_recaps_description),
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        )
    }
}

@Preview
@Composable
private fun OnboardingOverviewScreenPreview() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}
