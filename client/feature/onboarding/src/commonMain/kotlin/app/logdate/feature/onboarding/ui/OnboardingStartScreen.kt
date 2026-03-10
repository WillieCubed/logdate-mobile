@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.AdaptivePaneLayout
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_action_sign_in
import org.jetbrains.compose.resources.stringResource

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

    OnboardingStartScreenContent(
        showLanding = shouldShowMain,
        onGetStarted = onNext,
        onStartFromBackup = onStartFromBackup,
        modifier = modifier,
        useLargerTextSizes = useLargerTextSizes,
    )
}

@Composable
fun OnboardingStartScreenContent(
    showLanding: Boolean,
    onGetStarted: () -> Unit,
    onStartFromBackup: () -> Unit,
    modifier: Modifier = Modifier,
    useLargerTextSizes: Boolean = false,
) {
    AdaptivePaneLayout(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        contentPadding = PaddingValues(Spacing.lg),
        supportingPaneBreakpoint = 760.dp,
        supportingPaneWidth = 300.dp,
        mainPaneMinWidth = 320.dp,
        mainPaneMaxWidth = 520.dp,
        supportingPane = {
            OnboardingSupportPane(showLanding = showLanding)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                showLanding,
                label = "Main Content",
            ) { target ->
                if (target) {
                    OnboardingLandingContent(
                        onGetStarted = onGetStarted,
                        onStartFromBackup = onStartFromBackup,
                        useLargerTextSizes = useLargerTextSizes,
                    )
                } else {
                    OnboardingSplashContent()
                }
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
        modifier =
            Modifier
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
                    style =
                        if (useLargerTextSizes) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "A new home for your memories.",
                    style =
                        if (useLargerTextSizes) {
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
                    Text(stringResource(Res.string.get_started))
                }
                OutlinedButton(
                    onClick = onStartFromBackup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(Res.string.onboarding_action_sign_in),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingSupportPane(showLanding: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (showLanding) "Built for every window" else "A journal that grows with you",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text =
                    if (showLanding) {
                        "LogDate stays calm and readable on phones, tablets, and larger windows so onboarding never feels stretched or lost."
                    } else {
                        "Start on the device in your hand and keep going when you have more room. Your memories should feel at home anywhere."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Private timeline\nShared journals\nSync when you want it",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
