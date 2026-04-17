@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
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
import app.logdate.ui.AdaptiveLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.ui.generated.resources.book_open
import logdate.client.ui.generated.resources.common_back
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
    useSplitScreen: Boolean? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val resolvedUseSplitScreen = useSplitScreen ?: (maxWidth >= 700.dp)

        if (resolvedUseSplitScreen) {
            OverviewSplitLayout(onBack = onBack, onNext = onNext)
        } else {
            OverviewCompactLayout(onBack = onBack, onNext = onNext)
        }
    }
}

@Composable
private fun OverviewCompactLayout(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .testTag(ONBOARDING_OVERVIEW_ROOT_TAG)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 444.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(coreRes.string.common_back),
                        )
                    }
                }

                Column(
                    modifier = Modifier.widthIn(max = 444.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_overview_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = Spacing.lg),
                    )

                    OverviewCards()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.lg),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onNext,
                            modifier = Modifier.testTag(ONBOARDING_OVERVIEW_CONTINUE_TAG),
                        ) {
                            Text(text = stringResource(coreRes.string.common_continue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSplitLayout(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    AdaptiveLayout(
        useCompactLayout = false,
        modifier = Modifier.fillMaxSize().testTag(ONBOARDING_OVERVIEW_ROOT_TAG),
        supplementalContent = {
            // Left pane: title + subtitle with back arrow
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(coreRes.string.common_back),
                    )
                }
                Text(
                    stringResource(Res.string.onboarding_overview_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(Res.string.onboarding_overview_card_log_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        mainContent = {
            // Right pane: cards + Continue button
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OverviewCards()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.testTag(ONBOARDING_OVERVIEW_CONTINUE_TAG),
                    ) {
                        Text(text = stringResource(coreRes.string.common_continue))
                    }
                }
            }
        },
    )
}

@Composable
private fun OverviewCards() {
    OverviewItem(
        title = stringResource(Res.string.onboarding_overview_card_log_title),
        description = stringResource(Res.string.onboarding_overview_card_log_description),
        icon = {
            Icon(Icons.Rounded.Edit, contentDescription = null)
        },
    )
    OverviewItem(
        title = stringResource(Res.string.onboarding_overview_card_journals_title),
        description = stringResource(Res.string.onboarding_overview_card_journals_description),
        icon = {
            Icon(
                painterResource(coreRes.drawable.book_open),
                contentDescription = null,
            )
        },
    )
    OverviewItem(
        title = stringResource(Res.string.onboarding_overview_card_recaps_title),
        description = stringResource(Res.string.onboarding_overview_card_recaps_description),
        icon = {
            Icon(Icons.Rounded.History, contentDescription = null)
        },
    )
}

@Preview
@Composable
private fun OnboardingOverviewScreenPreview() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

@Preview(device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun OnboardingOverviewScreenPreview_Compact_Landscape() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {}, useSplitScreen = true)
    }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OnboardingOverviewScreenPreview_Medium_Landscape() {
    LogDateTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {}, useSplitScreen = true)
    }
}
