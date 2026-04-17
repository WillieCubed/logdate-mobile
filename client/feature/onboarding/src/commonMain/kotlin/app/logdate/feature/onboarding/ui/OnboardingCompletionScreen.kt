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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.onboarding.flow.OnboardingStep
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.action_onboarding_continue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

const val ONBOARDING_COMPLETION_ROOT_TAG = "onboarding_complete_root"
const val ONBOARDING_COMPLETION_CONTINUE_TAG = "onboarding_complete_continue"
const val ONBOARDING_COMPLETION_FINAL_TAG = "onboarding_complete_final"

/**
 * The last screen of the onboarding flow.
 *
 * This displays a message to the user about their streak and the completion of the onboarding flow.
 */
@Composable
fun OnboardingCompletionScreen(
    onFinish: () -> Unit,
    onRequirementsIncomplete: (OnboardingStep) -> Unit = {},
    viewModel: OnboardingViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    var shouldShowFinish by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    OnboardingCompletionContent(
        shouldShowFinish = shouldShowFinish,
        onContinue = { shouldShowFinish = true },
        onFinish = {
            coroutineScope.launch {
                viewModel
                    .completeOnboardingIfEligible()
                    .onSuccess {
                        onFinish()
                    }.onFailure {
                        viewModel
                            .firstIncompleteRequiredOnboardingStep()
                            ?.let(onRequirementsIncomplete)
                    }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun OnboardingCompletionContent(
    shouldShowFinish: Boolean,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var finalContentVisible by remember { mutableStateOf(true) }

    LaunchedEffect(shouldShowFinish) {
        if (shouldShowFinish) {
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
            CompletionStreakContent(onContinue = onContinue)
        }
    }
}

private const val ONBOARDING_COMPLETION_EXIT_FADE_MILLIS = 400

@Composable
private fun CompletionStreakContent(onContinue: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Spacing.lg)
                        .widthIn(max = 444.dp),
                verticalArrangement = Arrangement.spacedBy(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.one_more_thing), style = MaterialTheme.typography.headlineMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(Res.string.onboarding_completion_streak_begins),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    StreakCounterBox()
                    Text(
                        stringResource(Res.string.onboarding_completion_streak_encouragement),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onContinue,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.lg)
                        .fillMaxWidth()
                        .testTag(ONBOARDING_COMPLETION_CONTINUE_TAG),
            ) {
                Text(stringResource(Res.string.action_onboarding_continue))
            }
        }
    }
}

@Composable
private fun StreakCounterBox(count: Int = 1) {
    Box(
        modifier =
            Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.large,
                ).padding(16.dp),
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CompletionFinalContent() {
    Surface(
        modifier = Modifier.fillMaxSize().testTag(ONBOARDING_COMPLETION_FINAL_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.onboarding_completion_happy_logging),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview(
//    name = "Onboarding Final Screen 1"
)
@Composable
private fun PreviewCompletionStreakContent() {
    LogDateTheme {
        CompletionStreakContent(onContinue = {})
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
