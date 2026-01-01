package app.logdate.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.ui.theme.LogDateTheme

/**
 * Screenshot tests for onboarding flow screens.
 *
 * These previews are used by the Compose Screenshot Testing tool to generate
 * reference images for visual regression testing.
 */

@PreviewLightDark
@Composable
fun OnboardingStartScreen_Default() {
    LogDateTheme {
        OnboardingStartScreen(
            onNext = {},
            onStartFromBackup = {},
        )
    }
}

@PreviewLightDark
@Composable
fun OnboardingOverviewScreen_Default() {
    LogDateTheme {
        OnboardingOverviewScreen(
            onNext = {},
            onBack = {},
        )
    }
}
