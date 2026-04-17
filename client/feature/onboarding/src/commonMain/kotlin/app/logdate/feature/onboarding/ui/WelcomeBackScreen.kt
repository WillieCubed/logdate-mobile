@file:Suppress(
    "ktlint:standard:function-naming",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_welcome_back_description_streak_reset
import logdate.client.feature.onboarding.generated.resources.onboarding_welcome_back_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

const val WELCOME_BACK_ROOT_TAG = "onboarding_welcome_back_root"
private const val WELCOME_BACK_HOLD_MILLIS = 1_200L
private const val WELCOME_BACK_FADE_MILLIS = 360

/**
 * A screen shown to returning users after app setup.
 *
 * Content fades in on entry, holds briefly, then fades out before completing — avoiding the
 * jarring instant snap when the user leaves onboarding.
 */
@Composable
fun WelcomeBackScreen(
    onFinish: () -> Unit,
    viewModel: WelcomeBackViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    // I can't believe we have to use a view model for this
    val name by viewModel.nameState.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
        delay(WELCOME_BACK_HOLD_MILLIS)
        contentVisible = false
        delay(WELCOME_BACK_FADE_MILLIS.toLong())
        onFinish()
    }

    Surface(
        modifier = modifier.fillMaxSize().testTag(WELCOME_BACK_ROOT_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = WELCOME_BACK_FADE_MILLIS)),
            exit = fadeOut(animationSpec = tween(durationMillis = WELCOME_BACK_FADE_MILLIS)),
        ) {
            WelcomeBackScreenContent(name = name)
        }
    }
}

@Composable
fun WelcomeBackScreenContent(name: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.onboarding_welcome_back_title, name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(Res.string.onboarding_welcome_back_description_streak_reset),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun WelcomeBackScreenPreview() {
    LogDateTheme {
        WelcomeBackScreenContent(name = "Willie")
    }
}
