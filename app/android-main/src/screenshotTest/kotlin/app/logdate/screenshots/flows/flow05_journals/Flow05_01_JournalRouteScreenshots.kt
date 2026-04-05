package app.logdate.screenshots.flows.flow05_journals

import androidx.compose.runtime.Composable
import app.logdate.feature.journals.ui.creation.JournalCreationScreenContent
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.journals.ui.detail.SortOrder
import app.logdate.feature.journals.ui.settings.JournalSettingsScreenContent
import app.logdate.feature.journals.ui.settings.JournalSettingsUiState
import app.logdate.feature.journals.ui.share.ShareJournalScreenContent
import app.logdate.feature.journals.ui.share.ShareJournalUiState
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

private val journalEntries =
    listOf(
        EntryDisplayData.TextEntry(
            id = Uuid.parse("00000000-0000-0000-0000-000000000081"),
            content = "Finished wiring the route-level journal screenshots and trimmed the VM dependency surface.",
            timestamp = ScreenshotTestData.baseInstant,
        ),
        EntryDisplayData.TextEntry(
            id = Uuid.parse("00000000-0000-0000-0000-000000000082"),
            content = "Still need to normalize settings and note viewer coverage before baseline generation.",
            timestamp = ScreenshotTestData.baseInstant - 1.hours,
        ),
    )

private val populatedJournalState =
    JournalDetailUiState.Success(
        journalId = ScreenshotTestData.sampleJournal.id,
        title = ScreenshotTestData.sampleJournal.title,
        entries = journalEntries,
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_NewJournalEmpty() {
    ScreenshotTheme {
        JournalCreationScreenContent(
            onGoBack = {},
            onNewJournal = { _ -> },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_NewJournalFilled() {
    ScreenshotTheme {
        JournalCreationScreenContent(
            onGoBack = {},
            onNewJournal = { _ -> },
            initialTitle = "Route Screenshot Rollout",
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_JournalDetailLoading() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = JournalDetailUiState.Loading,
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_JournalDetailEmpty() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState =
                JournalDetailUiState.Success(
                    journalId = ScreenshotTestData.sampleJournal.id,
                    title = ScreenshotTestData.sampleJournal.title,
                    entries = emptyList(),
                ),
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_JournalDetailPopulated() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = populatedJournalState,
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_JournalDetailOldestFirst() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = populatedJournalState.copy(sortOrder = SortOrder.OLDEST_FIRST),
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_JournalDetailDeleteDialog() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = populatedJournalState,
            onGoBack = {},
            showDeleteConfirmation = true,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_JournalSettingsLoading() {
    ScreenshotTheme {
        JournalSettingsScreenContent(
            uiState = JournalSettingsUiState.Unknown,
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_JournalSettingsPristine() {
    ScreenshotTheme {
        JournalSettingsScreenContent(
            uiState =
                JournalSettingsUiState.Loaded(
                    journal = ScreenshotTestData.sampleJournal,
                    editedName = ScreenshotTestData.sampleJournal.title,
                    hasUnsavedChanges = false,
                ),
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_JournalSettingsDirty() {
    ScreenshotTheme {
        JournalSettingsScreenContent(
            uiState =
                JournalSettingsUiState.Loaded(
                    journal = ScreenshotTestData.sampleJournal,
                    editedName = "Route Screenshot Rollout",
                    hasUnsavedChanges = true,
                ),
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S11_JournalSettingsDeleteDialog() {
    ScreenshotTheme {
        JournalSettingsScreenContent(
            uiState =
                JournalSettingsUiState.Loaded(
                    journal = ScreenshotTestData.sampleJournal,
                    editedName = ScreenshotTestData.sampleJournal.title,
                    hasUnsavedChanges = false,
                ),
            onGoBack = {},
            showDeleteConfirmation = true,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S12_ShareJournalLoading() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState = ShareJournalUiState.Loading,
            onGoBack = {},
            onShareToInstagram = {},
            onShareQrCode = {},
            onShareJournal = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S13_ShareJournalSuccess() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState =
                ShareJournalUiState.Success(
                    journal = ScreenshotTestData.sampleJournal,
                    lastUpdatedDisplay = "Last updated Feb 20, 2025",
                ),
            onGoBack = {},
            onShareToInstagram = {},
            onShareQrCode = {},
            onShareJournal = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S14_ShareJournalError() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState = ShareJournalUiState.Error,
            onGoBack = {},
            onShareToInstagram = {},
            onShareQrCode = {},
            onShareJournal = {},
        )
    }
}
