package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.feature.onboarding.ui.OnboardingBirthdayContent
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesContent
import app.logdate.feature.onboarding.ui.OnboardingLocationContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationsContent
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest

private val tabletopPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 0.dp,
                        top = 438.dp,
                        right = 1440.dp,
                        bottom = 462.dp,
                        width = 1440.dp,
                        height = 24.dp,
                    ),
                isSeparating = true,
            ),
    )

private val bookPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = 900.dp,
                        width = 24.dp,
                        height = 900.dp,
                    ),
                isSeparating = true,
            ),
    )

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val TABLETOP_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val SAMPLE_IMAGE_URI =
    "android.resource://studio.hypertext.logdate.debug/drawable/sample_note_photo"
private const val SAMPLE_VIDEO_URI = "android.resource://studio.hypertext.logdate.debug/mipmap/ic_launcher"

@PreviewTest
@Preview(name = "Onboarding start book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A68_OnboardingStartBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingStartScreenContent(
                showLanding = true,
                onGetStarted = {},
                onSignIn = {},
                modifier = Modifier.fillMaxSize(),
                animateContent = false,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding start tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A69_OnboardingStartTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingStartScreenContent(
                showLanding = true,
                onGetStarted = {},
                onSignIn = {},
                modifier = Modifier.fillMaxSize(),
                animateContent = false,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Welcome back book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A70_WelcomeBackBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            WelcomeBackScreenContent(
                name = "Alex",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Welcome back tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A71_WelcomeBackTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            WelcomeBackScreenContent(
                name = "Alex",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding overview book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A72_OnboardingOverviewBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingOverviewScreen(
                onBack = {},
                onNext = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding overview tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A73_OnboardingOverviewTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingOverviewScreen(
                onBack = {},
                onNext = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Personal intro book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A74_PersonalIntroBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            PersonalIntroContent(
                uiState =
                    PersonalIntroUiState(
                        currentStep = PersonalIntroStep.Bio,
                        name = "Alex",
                        bio = "Runner, photographer, and obsessive note-taker who likes seeing context stay visible.",
                    ),
                onNameChanged = {},
                onBioChanged = {},
                onProceedToBio = {},
                onGoBackToName = {},
                onProcessWithLlm = {},
                onBack = {},
                modifier = Modifier.fillMaxSize(),
                autoFocusInputs = false,
                animateStepTransitions = false,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Personal intro tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A75_PersonalIntroTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            PersonalIntroContent(
                uiState =
                    PersonalIntroUiState(
                        currentStep = PersonalIntroStep.Bio,
                        name = "Alex",
                        bio = "Runner, photographer, and obsessive note-taker who likes seeing context stay visible.",
                    ),
                onNameChanged = {},
                onBioChanged = {},
                onProceedToBio = {},
                onGoBackToName = {},
                onProcessWithLlm = {},
                onBack = {},
                modifier = Modifier.fillMaxSize(),
                autoFocusInputs = false,
                animateStepTransitions = false,
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A76_OnboardingCompletionStreak() {
    ScreenshotTheme {
        OnboardingCompletionContent(
            shouldShowFinish = false,
            onContinue = {},
            onFinish = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding completion book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A77_OnboardingCompletionBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingCompletionContent(
                shouldShowFinish = false,
                onContinue = {},
                onFinish = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding completion tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A78_OnboardingCompletionTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingCompletionContent(
                shouldShowFinish = false,
                onContinue = {},
                onFinish = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A79_OnboardingNotificationsPrompt() {
    ScreenshotTheme {
        OnboardingNotificationsContent(
            onBack = {},
            onPrimaryAction = {},
            onSkip = {},
            recommendationsEnabled = true,
            hasDecision = false,
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding notifications book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A80_OnboardingNotificationsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingNotificationsContent(
                onBack = {},
                onPrimaryAction = {},
                onSkip = {},
                recommendationsEnabled = true,
                hasDecision = false,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding notifications tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A81_OnboardingNotificationsTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingNotificationsContent(
                onBack = {},
                onPrimaryAction = {},
                onSkip = {},
                recommendationsEnabled = true,
                hasDecision = false,
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A82_OnboardingBirthdayDefault() {
    ScreenshotTheme {
        OnboardingBirthdayContent(
            onBack = {},
            onBirthdaySelected = {},
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding birthday book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A83_OnboardingBirthdayBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingBirthdayContent(
                onBack = {},
                onBirthdaySelected = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding birthday tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A84_OnboardingBirthdayTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingBirthdayContent(
                onBack = {},
                onBirthdaySelected = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A85_OnboardingRecommendationsDefault() {
    ScreenshotTheme {
        OnboardingRecommendationsContent(
            onBack = {},
            onKeepOn = {},
            onTurnOff = {},
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding recommendations book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A86_OnboardingRecommendationsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingRecommendationsContent(
                onBack = {},
                onKeepOn = {},
                onTurnOff = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding recommendations tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A87_OnboardingRecommendationsTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingRecommendationsContent(
                onBack = {},
                onKeepOn = {},
                onTurnOff = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A88_OnboardingDayBoundariesDefault() {
    ScreenshotTheme {
        OnboardingDayBoundariesContent(
            gateState = HealthConnectGateState(kind = HealthConnectGateKind.READY),
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding day boundaries book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A89_OnboardingDayBoundariesBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingDayBoundariesContent(
                gateState = HealthConnectGateState(kind = HealthConnectGateKind.READY),
                onBack = {},
                onEnable = {},
                onSkip = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding day boundaries tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A90_OnboardingDayBoundariesTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingDayBoundariesContent(
                gateState = HealthConnectGateState(kind = HealthConnectGateKind.READY),
                onBack = {},
                onEnable = {},
                onSkip = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A91_OnboardingLocationDefault() {
    ScreenshotTheme {
        OnboardingLocationContent(
            onBack = {},
            onEnable = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@Preview(name = "Onboarding location book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A92_OnboardingLocationBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingLocationContent(
                onBack = {},
                onEnable = {},
                onSkip = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Onboarding location tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A93_OnboardingLocationTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingLocationContent(
                onBack = {},
                onEnable = {},
                onSkip = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Memories import info book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A94_MemoriesImportInfoBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(SharedScreenshotSceneId.MemoriesImportInfo)
        }
    }
}

@PreviewTest
@Preview(name = "Memories import info tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A95_MemoriesImportInfoTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(SharedScreenshotSceneId.MemoriesImportInfo)
        }
    }
}

@PreviewTest
@Preview(name = "Memory selection book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A96_MemorySelectionBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(SharedScreenshotSceneId.MemorySelectionEmpty)
        }
    }
}

@PreviewTest
@Preview(name = "Memory selection tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A97_MemorySelectionTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(SharedScreenshotSceneId.MemorySelectionEmpty)
        }
    }
}
