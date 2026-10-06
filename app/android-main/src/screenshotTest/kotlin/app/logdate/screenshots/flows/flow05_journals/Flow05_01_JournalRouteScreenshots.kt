package app.logdate.screenshots.flows.flow05_journals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.R
import app.logdate.feature.journals.ui.creation.JournalCreationScreenContent
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.journals.ui.detail.SortOrder
import app.logdate.feature.journals.ui.picker.JournalContentPickerDateGroup
import app.logdate.feature.journals.ui.picker.JournalContentPickerItem
import app.logdate.feature.journals.ui.picker.JournalContentPickerItemKind
import app.logdate.feature.journals.ui.picker.JournalContentPickerScreenContent
import app.logdate.feature.journals.ui.picker.JournalContentPickerUiState
import app.logdate.feature.journals.ui.settings.JournalSettingsScreenContent
import app.logdate.feature.journals.ui.settings.JournalSettingsUiState
import app.logdate.feature.journals.ui.share.ShareJournalScreenContent
import app.logdate.feature.journals.ui.share.ShareJournalUiState
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate

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

private val markdownPreviewJournalState =
    JournalDetailUiState.Success(
        journalId = ScreenshotTestData.sampleJournal.id,
        title = "Launch notes",
        entries =
            listOf(
                EntryDisplayData.TextEntry(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000083"),
                    content =
                        """# Offline-first polish

                            |The **local journal** stays writable even when LogDate Cloud is unavailable.

                            |- Record audio, photos, and video
                            |- Sync later without changing identity
                            |- Keep every pending edit durable
                            |
                            |> This final line must be visibly ellipsized in the collapsed card.
                        """.trimMargin(),
                    timestamp = ScreenshotTestData.baseInstant,
                ),
            ),
    )

private val pickerBrowseState =
    JournalContentPickerUiState(
        journalTitle = "Everyday moments",
        groups =
            listOf(
                JournalContentPickerDateGroup(
                    LocalDate(2025, 2, 20),
                    listOf(
                        JournalContentPickerItem(
                            Uuid.parse("00000000-0000-0000-0000-000000000084"),
                            JournalContentPickerItemKind.WRITING,
                            ScreenshotTestData.baseInstant,
                            "Coffee at the kitchen table while rain made the whole street quiet.",
                        ),
                        JournalContentPickerItem(
                            Uuid.parse("00000000-0000-0000-0000-000000000085"),
                            JournalContentPickerItemKind.PHOTO,
                            ScreenshotTestData.baseInstant - 1.hours,
                            "Rain on the windowsill",
                            "android.resource://studio.hypertext.logdate.debug/drawable/sample_note_photo",
                        ),
                        JournalContentPickerItem(
                            Uuid.parse("00000000-0000-0000-0000-000000000086"),
                            JournalContentPickerItemKind.RECORDING,
                            ScreenshotTestData.baseInstant - 2.hours,
                            "Rain against the glass",
                            "file:///sample-recording.m4a",
                        ),
                    ),
                ),
                JournalContentPickerDateGroup(
                    LocalDate(2025, 2, 19),
                    listOf(
                        JournalContentPickerItem(
                            Uuid.parse("00000000-0000-0000-0000-000000000087"),
                            JournalContentPickerItemKind.VIDEO,
                            ScreenshotTestData.baseInstant - 24.hours,
                            "Walk home at dusk",
                            "android.resource://studio.hypertext.logdate.debug/drawable/sample_note_photo",
                        ),
                    ),
                ),
            ),
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
                    journal = ScreenshotTestData.sharedJournal,
                    lastUpdatedDisplay = "Last updated Feb 20, 2025",
                ),
            onGoBack = {},
            onShareToInstagram = {},
            onShareQrCode = {},
            onShareJournal = {},
            previewCoverPainter = painterResource(R.drawable.sample_note_photo),
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

@PreviewTest
@ScreenshotPreviewMatrix
@Preview(
    name = "Phone 200% text",
    showBackground = true,
    device = PHONE,
    fontScale = 2f,
)
@Composable
fun S15_JournalDetailMarkdownPreview() {
    ScreenshotTheme {
        JournalDetailScreenContent(
            uiState = markdownPreviewJournalState,
            onGoBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S16_JournalContentPickerBrowse() {
    ScreenshotTheme {
        JournalContentPickerScreenContent(
            state = pickerBrowseState,
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S17_JournalContentPickerSelectedReview() {
    ScreenshotTheme {
        JournalContentPickerScreenContent(
            state = pickerBrowseState.copy(selectedItems = pickerBrowseState.groups.flatMap { it.items }.take(2)),
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Preview(name = "Phone 200% text", showBackground = true, device = PHONE, fontScale = 2f)
@Preview(name = "Phone RTL", showBackground = true, device = PHONE, locale = "ar")
@Composable
fun S18_JournalContentPickerSearch() {
    ScreenshotTheme {
        JournalContentPickerScreenContent(
            state =
                pickerBrowseState.copy(
                    query = "rain",
                    groups = listOf(pickerBrowseState.groups.first().copy(items = pickerBrowseState.groups.first().items.drop(1))),
                    selectedItems = listOf(pickerBrowseState.groups.first().items.first()),
                ),
            onBack = {},
        )
    }
}
