@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.streak.CampfireViewModel
import app.logdate.feature.onboarding.flow.OnboardingCompletionCoordinator
import app.logdate.feature.onboarding.flow.OnboardingFinishResult
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.ui.GenericLoadingScreen
import app.logdate.ui.platform.rememberLogDateHaptics
import app.logdate.ui.step.StepScaffold
import app.logdate.ui.streak.Campfire
import app.logdate.ui.streak.CampfirePhase
import app.logdate.ui.streak.CampfireSize
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

const val ONBOARDING_COMPLETION_ROOT_TAG = "onboarding_complete_root"
const val ONBOARDING_COMPLETION_CONTINUE_TAG = "onboarding_complete_continue"
const val ONBOARDING_COMPLETION_FINAL_TAG = "onboarding_complete_final"
const val ONBOARDING_COMPLETION_LOADING_TAG = "onboarding_complete_loading"

/**
 * The last screen of the onboarding flow.
 *
 * This displays a message to the user about their streak and the completion of the onboarding flow.
 */
@Composable
fun OnboardingCompletionScreen(
    onFinish: () -> Unit,
    onRequirementsIncomplete: (OnboardingStep) -> Unit = {},
    campfireViewModel: CampfireViewModel = koinViewModel(),
    completionCoordinator: OnboardingCompletionCoordinator = koinInject(),
    modifier: Modifier = Modifier,
) {
    var shouldShowFinish by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isCampfireEnabled by campfireViewModel.isCampfireEnabled.collectAsState()
    val showCampfire = isCampfireEnabled

    if (showCampfire == null) {
        GenericLoadingScreen(modifier = modifier.testTag(ONBOARDING_COMPLETION_LOADING_TAG))
        return
    }

    val finishOnboarding: () -> Unit = {
        coroutineScope.launch {
            when (val result = completionCoordinator.finishOnboarding()) {
                OnboardingFinishResult.Finished -> onFinish()
                is OnboardingFinishResult.IncompleteStep -> onRequirementsIncomplete(result.step)
                is OnboardingFinishResult.SaveFailed -> saveFailed = true
            }
        }
    }

    if (saveFailed) {
        OnboardingSaveFailedContent(
            onRetry = {
                saveFailed = false
                finishOnboarding()
            },
            modifier = modifier,
        )
        return
    }

    OnboardingCompletionContent(
        shouldShowFinish = shouldShowFinish,
        showCampfire = showCampfire,
        onContinue = { shouldShowFinish = true },
        onFinish = finishOnboarding,
        modifier = modifier,
    )
}

@Composable
fun OnboardingCompletionContent(
    shouldShowFinish: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    showCampfire: Boolean = false,
) {
    var finalContentVisible by remember { mutableStateOf(true) }
    val haptics = rememberLogDateHaptics()

    LaunchedEffect(shouldShowFinish) {
        if (shouldShowFinish) {
            haptics.saveSucceeded()
            // Hold the final message, then fade out before leaving onboarding so the
            // next screen doesn't snap in on top of "Happy logging!"
            delay(1_600)
            finalContentVisible = false
            delay(ONBOARDING_COMPLETION_EXIT_FADE_MILLIS.toLong())
            onFinish()
        }
    }

    AnimatedContent(
        targetState = shouldShowFinish,
        modifier = modifier.testTag(ONBOARDING_COMPLETION_ROOT_TAG),
        transitionSpec = { onboardingFadeTransition() },
        label = "Show Finish Screen",
    ) { isShowingFinish ->
        if (isShowingFinish) {
            AnimatedVisibility(
                visible = finalContentVisible,
                enter = fadeIn(tween(ONBOARDING_COMPLETION_EXIT_FADE_MILLIS)),
                exit = fadeOut(tween(ONBOARDING_COMPLETION_EXIT_FADE_MILLIS)),
            ) {
                CompletionFinalContent()
            }
        } else {
            CompletionStreakContent(onContinue = onContinue, showCampfire = showCampfire)
        }
    }
}

private const val ONBOARDING_COMPLETION_EXIT_FADE_MILLIS = 400

@Composable
private fun CompletionStreakContent(
    onContinue: () -> Unit,
    showCampfire: Boolean,
) {
    val title: String
    val encouragement: String
    if (showCampfire) {
        title = stringResource(Res.string.onboarding_completion_fire_lit)
        encouragement = stringResource(Res.string.onboarding_completion_fire_encouragement)
    } else {
        title = stringResource(Res.string.onboarding_completion_streak_begins)
        encouragement = stringResource(Res.string.onboarding_completion_streak_encouragement)
    }

    StepScaffold(
        title = title,
        onBack = null,
        supportingText = encouragement,
        headerAlignment = Alignment.CenterHorizontally,
        hero = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showCampfire) {
                    Campfire(
                        phase = CampfirePhase.BURNING,
                        size = CampfireSize.SPARK,
                        contentDescription = title,
                        modifier = Modifier.size(128.dp),
                    )
                } else {
                    StreakCounter(count = 1)
                }
                Spacer(modifier = Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(Res.string.one_more_thing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_COMPLETION_CONTINUE_TAG),
            ) {
                Text(stringResource(Res.string.action_onboarding_continue))
            }
        },
    )
}

@Composable
private fun StreakCounter(count: Int) {
    Column(
        modifier =
            Modifier
                .size(112.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(Res.string.onboarding_completion_streak_day_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CompletionFinalContent() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag(ONBOARDING_COMPLETION_FINAL_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.onboarding_completion_happy_logging),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(
//    name = "Onboarding Final Screen 1"
)
@Composable
private fun PreviewCompletionStreakContent() {
    LogDateTheme {
        CompletionStreakContent(onContinue = {}, showCampfire = false)
    }
}

@Preview(
//    name = "Onboarding Final Screen 2"
)
@Composable
private fun PreviewCompletionFinalContent() {
    LogDateTheme {
        CompletionFinalContent()
    }
}
