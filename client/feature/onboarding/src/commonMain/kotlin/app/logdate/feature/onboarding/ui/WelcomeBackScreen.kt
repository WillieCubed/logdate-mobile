@file:Suppress(
    "ktlint:standard:function-naming",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
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
            WelcomeBackScreenContent(name = name, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun WelcomeBackScreenContent(
    name: String,
    modifier: Modifier = Modifier,
) {
    FoldableTabletopLayout(
        modifier = modifier,
        minPaneHeight = 220.dp,
        topPane = {
            WelcomeBackTitlePane(name = name, modifier = Modifier.fillMaxSize())
        },
        bottomPane = {
            WelcomeBackBodyPane(modifier = Modifier.fillMaxSize())
        },
        fallback = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize(),
                minPaneWidth = 320.dp,
                startPane = {
                    WelcomeBackTitlePane(name = name, modifier = Modifier.fillMaxSize())
                },
                endPane = {
                    WelcomeBackBodyPane(modifier = Modifier.fillMaxSize())
                },
                standardContent = {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WelcomeBackTitle(name = name)
                        WelcomeBackBody()
                    }
                },
            )
        },
    )
}

@Composable
private fun WelcomeBackTitlePane(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        WelcomeBackTitle(name = name)
    }
}

@Composable
private fun WelcomeBackBodyPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        WelcomeBackBody()
    }
}

@Composable
private fun WelcomeBackTitle(name: String) {
    Text(
        text = stringResource(Res.string.onboarding_welcome_back_title, name),
        modifier = Modifier.widthIn(max = 420.dp),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WelcomeBackBody() {
    Text(
        text = stringResource(Res.string.onboarding_welcome_back_description_streak_reset),
        modifier = Modifier.widthIn(max = 420.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun WelcomeBackScreenPreview() {
    LogDateTheme {
        WelcomeBackScreenContent(name = "Willie")
    }
}
