package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_action_sign_in
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val DELAY_TIME = 1_000L

/**
 * An onboarding screen that
 */
@Composable
fun OnboardingStartScreen(
    onNext: () -> Unit,
    onStartFromBackup: () -> Unit,
    modifier: Modifier = Modifier,
    useLargerTextSizes: Boolean = false,
) {
    var shouldShowMain by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowMain) {
        delay(DELAY_TIME * 3)
        shouldShowMain = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(Spacing.lg)
    ) {
        AnimatedContent(
            shouldShowMain,
            label = "Main Content",
            modifier = Modifier.align(Alignment.Center),
        ) { target ->
            if (target) {
                OnboardingLandingContent(
                    onGetStarted = onNext,
                    onStartFromBackup = onStartFromBackup,
                    useLargerTextSizes = useLargerTextSizes,
                )
            } else {
                OnboardingSplashContent()
            }

        }
    }
}

@Composable
private fun OnboardingSplashContent() {
    var shouldShowSubheading by remember { mutableStateOf(false) }

    LaunchedEffect(shouldShowSubheading) {
        delay(DELAY_TIME)
        shouldShowSubheading = true
    }

    Column(
        modifier = Modifier.widthIn(max = 444.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg * 4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Imagine if you had a journal that grew with you and the people you cared about.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
        AnimatedContent(
            shouldShowSubheading,
            label = "Subheading",
        ) { target ->
            if (target) {
                Text(
                    "No need to imagine anymore.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun OnboardingLandingContent(
    onGetStarted: () -> Unit,
    onStartFromBackup: () -> Unit,
    useLargerTextSizes: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            // TODO: Reduce spacing between buttons and title block on screens with less height
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(96.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Welcome to LogDate.",
                    style = if (useLargerTextSizes) {
                        MaterialTheme.typography.displaySmall
                    } else {
                        MaterialTheme.typography.headlineLarge
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "A new home for your memories.",
                    style = if (useLargerTextSizes) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.widthIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalButton(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Get Started")
                }
                OutlinedButton(
                    onClick = onStartFromBackup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_action_sign_in),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

}

@Preview(
//    name = "Splash Screen - Size Compact", group = "Compact Devices"
)
@Composable
fun OnboardingStartScreenPreview() {
    LogDateTheme {
        OnboardingStartScreen(onNext = {}, onStartFromBackup = {})
    }
}

@Preview(
//    name = "Splash Screen Loaded - Size Compact", group = "Compact Devices"
)
@Composable
fun OnboardingSplashContentPreview() {
    LogDateTheme {
        OnboardingLandingContent(onGetStarted = {}, onStartFromBackup = {})
    }
}

@Preview(
//    device = "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240",
//    name = "Splash Screen Loaded - Size Medium"
)
@Composable
fun OnboardingSplashContentPreview_Medium() {
    LogDateTheme {
        OnboardingLandingContent(
            onGetStarted = {},
            onStartFromBackup = {},
            useLargerTextSizes = true,
        )
    }
}