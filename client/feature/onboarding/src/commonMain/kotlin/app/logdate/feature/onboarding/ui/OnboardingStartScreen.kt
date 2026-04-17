@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import logdate.client.feature.onboarding.generated.resources.*
import logdate.client.feature.onboarding.generated.resources.Res
import logdate.client.feature.onboarding.generated.resources.onboarding_action_sign_in
import logdate.client.ui.generated.resources.common_get_started
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

private const val DELAY_TIME = 1_000L
const val ONBOARDING_START_ROOT_TAG = "onboarding_start_root"
const val ONBOARDING_START_GET_STARTED_TAG = "onboarding_start_get_started"
const val ONBOARDING_START_FROM_BACKUP_TAG = "onboarding_start_from_backup"

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
    animateContent: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .testTag(ONBOARDING_START_ROOT_TAG)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (animateContent) {
                AnimatedContent(
                    showLanding,
                    transitionSpec = { onboardingFadeTransition() },
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
            } else if (showLanding) {
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
            stringResource(Res.string.onboarding_start_tagline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
        AnimatedContent(
            shouldShowSubheading,
            transitionSpec = { onboardingFadeTransition() },
            label = "Subheading",
        ) { target ->
            if (target) {
                Text(
                    stringResource(Res.string.onboarding_start_tagline_subheading),
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
                    stringResource(Res.string.onboarding_start_welcome_title),
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
                    stringResource(Res.string.onboarding_start_welcome_subtitle),
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(ONBOARDING_START_GET_STARTED_TAG)
                            .semantics {
                                contentDescription = ONBOARDING_START_GET_STARTED_TAG
                            },
                ) {
                    Text(stringResource(UiRes.string.common_get_started))
                }
                OutlinedButton(
                    onClick = onStartFromBackup,
                    modifier = Modifier.fillMaxWidth().testTag(ONBOARDING_START_FROM_BACKUP_TAG),
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
