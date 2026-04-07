@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

const val ONBOARDING_RECOMMENDATIONS_ROOT_TAG = "onboarding_recommendations_root"
const val ONBOARDING_RECOMMENDATIONS_KEEP_ON_TAG = "onboarding_recommendations_keep_on"
const val ONBOARDING_RECOMMENDATIONS_TURN_OFF_TAG = "onboarding_recommendations_turn_off"

@Composable
fun OnboardingRecommendationsScreen(
    onBack: () -> Unit,
    onNext: (Boolean) -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    OnboardingRecommendationsContent(
        onBack = onBack,
        onKeepOn = {
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .persistRecommendationsEnabled(true)
                    .onSuccess {
                        viewModel
                            .markRecommendationsHandled()
                            .onSuccess {
                                isSaving = false
                                onNext(true)
                            }.onFailure {
                                errorMessage = "We couldn't save your recommendations setting right now."
                                isSaving = false
                            }
                    }.onFailure {
                        errorMessage = "We couldn't save your recommendations setting right now."
                        isSaving = false
                    }
            }
        },
        onTurnOff = {
            coroutineScope.launch {
                isSaving = true
                errorMessage = null
                viewModel
                    .persistRecommendationsEnabled(false)
                    .onSuccess {
                        viewModel
                            .markRecommendationsHandled()
                            .onSuccess {
                                isSaving = false
                                onNext(false)
                            }.onFailure {
                                errorMessage = "We couldn't save your recommendations setting right now."
                                isSaving = false
                            }
                    }.onFailure {
                        errorMessage = "We couldn't save your recommendations setting right now."
                        isSaving = false
                    }
            }
        },
        isSaving = isSaving,
        errorMessage = errorMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingRecommendationsContent(
    onBack: () -> Unit,
    onKeepOn: () -> Unit,
    onTurnOff: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    val scrollState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(scrollState)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(Res.string.onboarding_recommendations_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(UiRes.string.common_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .testTag(ONBOARDING_RECOMMENDATIONS_ROOT_TAG)
                    .fillMaxHeight()
                    .widthIn(max = 444.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = contentPadding.calculateTopPadding() + Spacing.lg,
                    bottom = contentPadding.calculateBottomPadding() + Spacing.lg,
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr) + Spacing.lg,
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr) + Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    stringResource(Res.string.onboarding_recommendations_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_recommendations_card_drafts_title),
                    description = stringResource(Res.string.onboarding_recommendations_card_drafts_description),
                    icon = {
                        Icon(Icons.Rounded.EditNote, contentDescription = null)
                    },
                )
            }
            item {
                OverviewItem(
                    title = stringResource(Res.string.onboarding_recommendations_card_recall_title),
                    description = stringResource(Res.string.onboarding_recommendations_card_recall_description),
                    icon = {
                        Icon(Icons.Rounded.History, contentDescription = null)
                    },
                )
            }
            item {
                // Privacy note
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.shapes.medium,
                            ).padding(Spacing.md),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_recommendations_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = onKeepOn,
                        modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_RECOMMENDATIONS_KEEP_ON_TAG),
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(Res.string.onboarding_recommendations_keep_on))
                        }
                    }
                    TextButton(
                        onClick = onTurnOff,
                        modifier = Modifier.testTag(ONBOARDING_RECOMMENDATIONS_TURN_OFF_TAG),
                    ) {
                        Text(stringResource(Res.string.onboarding_recommendations_turn_off))
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingRecommendationsScreenPreview() {
    LogDateTheme {
        OnboardingRecommendationsContent(
            onBack = {},
            onKeepOn = {},
            onTurnOff = {},
        )
    }
}
