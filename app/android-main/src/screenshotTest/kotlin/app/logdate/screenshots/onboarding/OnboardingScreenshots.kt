package app.logdate.screenshots.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.onboarding.ui.MemorySelectionUiState
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationConfirmationContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationContent
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.onboarding.ui.MemoriesImportInfoScreen
import app.logdate.feature.onboarding.ui.MemorySelectionScreen
import app.logdate.feature.onboarding.ui.BackupSyncScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.RecoveryPhraseSetupScreen
import app.logdate.feature.onboarding.ui.RecoveryPhraseEntryScreen
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

// ─── Start Screen ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun OnboardingStart() {
    ScreenshotTheme {
        OnboardingStartScreen(onNext = {}, onStartFromBackup = {})
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OnboardingStart_Dark() {
    ScreenshotTheme(darkTheme = true) {
        OnboardingStartScreen(onNext = {}, onStartFromBackup = {})
    }
}

// ─── Personal Intro ─────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun PersonalIntro_Empty() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState = PersonalIntroUiState(),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun PersonalIntro_NameFilled() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState = PersonalIntroUiState(
                name = "Alex",
                currentStep = PersonalIntroStep.Name,
            ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun PersonalIntro_BioStep() {
    ScreenshotTheme {
        PersonalIntroContent(
            uiState = PersonalIntroUiState(
                name = "Alex",
                bio = "I love hiking and photography",
                currentStep = PersonalIntroStep.Bio,
            ),
            onNameChanged = {},
            onBioChanged = {},
            onProceedToBio = {},
            onGoBackToName = {},
            onProcessWithLlm = {},
            onBack = {},
        )
    }
}

// ─── App Overview ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun AppOverview() {
    ScreenshotTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

// ─── Memories Import ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MemoriesImportInfo() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

// ─── Memory Selection ───────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun MemorySelection_Empty() {
    ScreenshotTheme {
        MemorySelectionScreen(
            uiState = MemorySelectionUiState(isLoading = false, hasMoreMemories = false),
            onBack = {},
            onContinue = {},
            onToggleMemorySelection = {},
            onLoadMoreMemories = {},
        )
    }
}

// ─── Notification Screens ───────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun NotificationPermission() {
    ScreenshotTheme {
        OnboardingNotificationContent(
            onBack = {},
            useCompactLayout = true,
            onEnableNotifications = {},
            onSkipNotifications = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun NotificationConfirmation() {
    ScreenshotTheme {
        OnboardingNotificationConfirmationContent(
            onBack = {},
            onNext = {},
            useCompactLayout = true,
        )
    }
}

// ─── Backup & Sync ──────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun BackupSync() {
    ScreenshotTheme {
        BackupSyncScreenContent(
            useCompactLayout = true,
            onBack = {},
            onPlanSelected = {},
        )
    }
}

// ─── Recovery Phrase ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryPhraseSetup() {
    ScreenshotTheme {
        RecoveryPhraseSetupScreen(onPhraseContinue = {})
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun RecoveryPhraseEntry() {
    ScreenshotTheme {
        RecoveryPhraseEntryScreen(onRecovered = {})
    }
}

// ─── Completion ─────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun OnboardingCompletion_Streak() {
    ScreenshotTheme {
        OnboardingCompletionContent(
            shouldShowFinish = false,
            onContinue = {},
            onFinish = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun OnboardingCompletion_Final() {
    ScreenshotTheme {
        OnboardingCompletionContent(
            shouldShowFinish = true,
            onContinue = {},
            onFinish = {},
        )
    }
}

// ─── Welcome Back ───────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun WelcomeBack() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}
