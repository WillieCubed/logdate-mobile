package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.onboarding.R
import kotlinx.coroutines.delay

/**
 * The last screen of the onboarding flow.
 *
 * This displays a message to the user about their streak and the completion of the onboarding flow.
 */
@Composable
internal fun OnboardingCompletionScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var shouldShowFinish by remember { mutableStateOf(false) }

    OnboardingCompletionContent(
        shouldShowFinish = shouldShowFinish,
        onContinue = { shouldShowFinish = true },
        onFinish = {
            viewModel.completeOnboarding()
            onFinish()
        },
    )
}

@Composable
private fun OnboardingCompletionContent(
    shouldShowFinish: Boolean, onContinue: () -> Unit, onFinish: () -> Unit,
) {
    LaunchedEffect(shouldShowFinish) {
        if (shouldShowFinish) {
            delay(2_000)
            onFinish()
        }
    }

    AnimatedContent(
        shouldShowFinish,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(3000)
            ) togetherWith fadeOut(
                animationSpec = tween(3000)
            )
        },
        label = "Show Finish Screen"
    ) { isShowingFinish ->
        if (isShowingFinish) {
            CompletionFinalContent()
        } else {
            CompletionStreakContent(onContinue = onContinue)
        }

    }
}

@Composable
private fun CompletionStreakContent(
    onContinue: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(app.logdate.ui.theme.Spacing.lg)
                    .widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("One more thing...", style = MaterialTheme.typography.headlineMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(app.logdate.ui.theme.Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    Text(
                        "Your streak begins today.", style = MaterialTheme.typography.headlineMedium
                    )
                    StreakCounterBox()
                    Text(
                        "Write, photograph, or record something every day to keep it up.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Button(
                onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(app.logdate.ui.theme.Spacing.lg)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_onboarding_continue))
            }
        }
    }
}

@Composable
private fun StreakCounterBox(
    count: Int = 1
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large
            )
            .padding(16.dp),
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
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Happy logging!", style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview(name = "Onboarding Final Screen 1")
@Composable
private fun PreviewCompletionStreakContent() {
    app.logdate.ui.theme.LogDateTheme {
        CompletionStreakContent(onContinue = {})
    }
}

@Preview(name = "Onboarding Final Screen 2")
@Composable
private fun PreviewCompletionFinalContent() {
    app.logdate.ui.theme.LogDateTheme {
        CompletionFinalContent()
    }
}