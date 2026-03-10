package app.logdate.screenshots.flows.flow01_onboarding

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
fun S01_OnboardingStart() {
    ScreenshotTheme {
        OnboardingStartScreen(onNext = {}, onStartFromBackup = {})
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun S02_OnboardingStartDark() {
    ScreenshotTheme(darkTheme = true) {
        OnboardingStartScreen(onNext = {}, onStartFromBackup = {})
    }
}

// ─── Personal Intro ─────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S03_PersonalIntroEmpty() {
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
fun S04_PersonalIntroNameFilled() {
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
fun S05_PersonalIntroBioStep() {
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
fun S06_AppOverview() {
    ScreenshotTheme {
        OnboardingOverviewScreen(onBack = {}, onNext = {})
    }
}

// ─── Memories Import ────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S07_MemoriesImportInfo() {
    ScreenshotTheme {
        MemoriesImportInfoScreen(onBack = {}, onContinue = {})
    }
}

// ─── Memory Selection ───────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S08_MemorySelectionEmpty() {
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
fun S09_NotificationPermission() {
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
fun S10_NotificationConfirmation() {
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
fun S11_BackupSync() {
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
fun S12_RecoveryPhraseSetup() {
    ScreenshotTheme {
        RecoveryPhraseSetupScreen(onPhraseContinue = {})
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S13_RecoveryPhraseEntry() {
    ScreenshotTheme {
        RecoveryPhraseEntryScreen(onRecovered = {})
    }
}

// ─── Completion ─────────────────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S14_OnboardingCompletionStreak() {
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
fun S15_OnboardingCompletionFinal() {
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
fun S16_WelcomeBack() {
    ScreenshotTheme {
        WelcomeBackScreenContent(name = "Alex")
    }
}
