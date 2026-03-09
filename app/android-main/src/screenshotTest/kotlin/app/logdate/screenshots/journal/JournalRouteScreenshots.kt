package app.logdate.screenshots.journal

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
        EntryDisplayData(
            id = Uuid.parse("00000000-0000-0000-0000-000000000081"),
            content = "Finished wiring the route-level journal screenshots and trimmed the VM dependency surface.",
            timestamp = ScreenshotTestData.baseInstant,
        ),
        EntryDisplayData(
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
fun NewJournalRoute_Empty() {
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
fun NewJournalRoute_Filled() {
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
fun JournalDetailRoute_Loading() {
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
fun JournalDetailRoute_Empty() {
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
fun JournalDetailRoute_Populated() {
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
fun JournalDetailRoute_OldestFirst() {
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
fun JournalDetailRoute_DeleteDialog() {
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
fun JournalSettingsRoute_Loading() {
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
fun JournalSettingsRoute_Pristine() {
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
fun JournalSettingsRoute_Dirty() {
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
fun JournalSettingsRoute_DeleteDialog() {
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
fun ShareJournalRoute_Loading() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState = ShareJournalUiState.Loading,
            onGoBack = {},
            onShareToInstagram = {},
            onShareJournal = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun ShareJournalRoute_Success() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState =
                ShareJournalUiState.Success(
                    journal = ScreenshotTestData.sampleJournal,
                    lastUpdatedDisplay = "Last updated Feb 20, 2025",
                ),
            onGoBack = {},
            onShareToInstagram = {},
            onShareJournal = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun ShareJournalRoute_Error() {
    ScreenshotTheme {
        ShareJournalScreenContent(
            uiState = ShareJournalUiState.Error,
            onGoBack = {},
            onShareToInstagram = {},
            onShareJournal = {},
        )
    }
}
