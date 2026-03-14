package app.logdate.screenshots.audit.adaptive

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchResultType
import app.logdate.feature.core.account.ui.CloudAccountIntroContent
import app.logdate.feature.core.profile.ui.ProfileScreenContent
import app.logdate.feature.core.profile.ui.ProfileUiState
import app.logdate.feature.journals.ui.JournalLayoutMode
import app.logdate.feature.journals.ui.JournalListItemUiState
import app.logdate.feature.journals.ui.JournalSortOption
import app.logdate.feature.journals.ui.JournalsOverviewScreenContent
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

private val auditSearchResults =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000121"),
            content = "Captured the train ride home before dinner and tagged it for the weekly rewind.",
            created = ScreenshotTestData.baseInstant,
            type = SearchResultType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000122"),
            content = "Voice memo about redesigning the journals overview for larger windows.",
            created = ScreenshotTestData.baseInstant - 2.hours,
            type = SearchResultType.TRANSCRIPTION,
        ),
    )

private val auditJournalEntries =
    listOf(
        EntryDisplayData(
            id = Uuid.parse("00000000-0000-0000-0000-000000000131"),
            content = "Moved the journal list into a deterministic audit harness for tablet and desktop previews.",
            timestamp = ScreenshotTestData.baseInstant,
        ),
        EntryDisplayData(
            id = Uuid.parse("00000000-0000-0000-0000-000000000132"),
            content = "Trimmed excess horizontal padding so medium-width windows keep both context and readability.",
            timestamp = ScreenshotTestData.baseInstant - 3.hours,
        ),
    )

private val auditJournalState =
    JournalDetailUiState.Success(
        journalId = ScreenshotTestData.sampleJournal.id,
        title = ScreenshotTestData.sampleJournal.title,
        entries = auditJournalEntries,
    )

private val auditJournals =
    ScreenshotTestData.sampleJournals.map { journal ->
        JournalListItemUiState.ExistingJournal(journal)
    } +
        JournalListItemUiState.CreateJournalPlaceholder

private val auditProfile = LogDateProfile(displayName = "Alex Johnson")
private val auditAccount =
    LogDateAccount(
        username = "alex_j",
        displayName = "Alex Johnson",
        passkeyCredentialIds = listOf("credential-1"),
    )

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A01_OnboardingStartLanding() {
    ScreenshotTheme {
        OnboardingStartScreenContent(
            showLanding = true,
            onGetStarted = {},
            onStartFromBackup = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A02_PersonalIntroBioStep() {
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
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A03_CloudAccountSetup() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = false,
            selectedOption = null,
            onBack = {},
            onOptionSelected = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = { _ -> },
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A04_CloudAccountIntro() {
    ScreenshotTheme {
        CloudAccountIntroContent(
            isFromOnboarding = false,
            onContinue = {},
            onSkip = {},
            onBack = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A05_SearchWithResults() {
    ScreenshotTheme {
        SearchScreenContent(
            query = "window",
            searchResults = auditSearchResults,
            onQueryChange = {},
            onClearSearch = {},
            onNavigateToDay = {},
            onGoBack = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A06_JournalsOverview() {
    ScreenshotTheme {
        JournalsOverviewScreenContent(
            journals = auditJournals,
            layoutMode = JournalLayoutMode.CAROUSEL,
            sortOption = JournalSortOption.LAST_UPDATED,
            activeFilters = emptySet(),
            onOpenJournal = {},
            onBrowseJournals = {},
            onCreateJournal = {},
            onNavigationClick = {},
            onToggleLayoutMode = {},
            onSortOptionSelected = {},
            onToggleFilter = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A07_JournalDetail() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = auditJournalState,
            onGoBack = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A08_ProfileDefault() {
    val snackbarHostState = remember { SnackbarHostState() }

    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = auditProfile,
                    account = auditAccount,
                    userData = null,
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}
