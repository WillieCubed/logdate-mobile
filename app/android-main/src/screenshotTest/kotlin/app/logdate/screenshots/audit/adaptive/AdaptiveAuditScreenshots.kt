package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.R
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.repository.search.SearchContentType
import app.logdate.feature.core.account.CloudAccountWelcomeContent
import app.logdate.feature.core.account.CloudAccountSignInContent
import app.logdate.feature.core.account.PasskeyAccountCreationFinalContent
import app.logdate.feature.core.export.ExportOptions
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.ImportOptions
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.settings.ui.ClearDataSettingsContent
import app.logdate.feature.core.settings.ui.DataSettingsContent
import app.logdate.feature.core.settings.ui.ServerSelectionState
import app.logdate.feature.core.settings.ui.ResetAppSettingsContent
import app.logdate.feature.core.settings.ui.ResetSettingsScreen
import app.logdate.feature.core.settings.ui.ConflictsState
import app.logdate.feature.core.settings.ui.IntegrityState
import app.logdate.feature.core.settings.ui.StorageQuotaUi
import app.logdate.feature.core.profile.ui.ProfileEditState
import app.logdate.feature.core.profile.ui.ProfileScreenContent
import app.logdate.feature.core.profile.ui.ProfileUiState
import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.feature.editor.audio.AudioContext
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.audio.model.SegmentType
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.feature.editor.ui.audio.AudioRecordingControls
import app.logdate.feature.editor.ui.audio.AudioTranscriptionUi
import app.logdate.feature.editor.ui.audio.AudioUiState
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.RecordingState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.feature.journals.ui.JournalLayoutMode
import app.logdate.feature.journals.ui.JournalListItemUiState
import app.logdate.feature.journals.ui.JournalSortOption
import app.logdate.feature.journals.ui.JournalsOverviewScreenContent
import app.logdate.feature.journals.ui.creation.JournalCreationScreenContent
import app.logdate.feature.journals.ui.detail.AudioNoteViewerContent
import app.logdate.feature.journals.ui.detail.AudioNoteViewerUiState
import app.logdate.feature.journals.ui.detail.AudioPlaybackUiState
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.journals.ui.detail.JournalContext
import app.logdate.feature.journals.ui.detail.NoteViewerScaffoldContent
import app.logdate.feature.journals.ui.detail.NoteViewerShared
import app.logdate.feature.journals.ui.settings.JournalSettingsScreenContent
import app.logdate.feature.journals.ui.settings.JournalSettingsUiState
import app.logdate.feature.journals.ui.share.ShareJournalScreenContent
import app.logdate.feature.journals.ui.share.ShareJournalUiState
import app.logdate.feature.library.ui.detail.MediaDetailContent
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.onboarding.ui.BackupSyncScreenContent
import app.logdate.feature.onboarding.ui.CloudAccountSetupContent
import app.logdate.feature.onboarding.ui.OnboardingBirthdayContent
import app.logdate.feature.onboarding.ui.OnboardingCompletionContent
import app.logdate.feature.onboarding.ui.OnboardingDayBoundariesContent
import app.logdate.feature.onboarding.ui.OnboardingNotificationsContent
import app.logdate.feature.onboarding.ui.OnboardingOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingLocationContent
import app.logdate.feature.onboarding.ui.OnboardingRecommendationsContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.onboarding.ui.RecoveryPhraseEntryScreen
import app.logdate.feature.onboarding.ui.RecoveryPhraseSetupScreen
import app.logdate.feature.onboarding.ui.PersonalIntroContent
import app.logdate.feature.onboarding.ui.PersonalIntroStep
import app.logdate.feature.onboarding.ui.PersonalIntroUiState
import app.logdate.feature.onboarding.ui.WelcomeBackScreenContent
import app.logdate.feature.search.ui.SearchScreenContent
import app.logdate.feature.search.ui.SearchScreenState
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.screenshots.components.library.LibraryScreenshotData
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
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
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.DayEventUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.VideoNoteUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val TABLETOP_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val SAMPLE_IMAGE_URI =
    "file:///Users/williecubed/Projects/TheHypertextStudio/logdate-android/app/android-main/src/debug/res/drawable-nodpi/sample_note_photo.jpg"
private const val SAMPLE_VIDEO_URI = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher"

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

private val auditSearchResults =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000121"),
            content = "Captured the train ride home before dinner and tagged it for the weekly rewind.",
            created = ScreenshotTestData.baseInstant,
            contentType = SearchContentType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000122"),
            content = "Voice memo about redesigning the journals overview for larger windows.",
            created = ScreenshotTestData.baseInstant - 2.hours,
            contentType = SearchContentType.TRANSCRIPTION,
        ),
    )

private val auditJournalEntries =
    listOf(
        EntryDisplayData.TextEntry(
            id = Uuid.parse("00000000-0000-0000-0000-000000000131"),
            content = "Moved the journal list into a deterministic audit harness for tablet and desktop previews.",
            timestamp = ScreenshotTestData.baseInstant,
        ),
        EntryDisplayData.TextEntry(
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

private val auditStorageQuota =
    StorageQuotaUi(
        totalBytes = 5_368_709_120L,
        usedBytes = 2_147_483_648L,
        usagePercentage = 0.4f,
        formattedTotal = "5.0 GB",
        formattedUsed = "2.0 GB",
    )

private val auditExportCounts =
    ExportCounts(
        journalCount = 18,
        noteCount = 126,
        draftCount = 7,
        mediaCount = 42,
    )

private val auditArchivePreview =
    ArchivePreview(
        version = ExportSchemaVersion.V1_2,
        exportDate = ScreenshotTestData.baseInstant,
        appVersion = "0.1.0",
        stats =
            ExportStats(
                journalCount = 18,
                noteCount = 126,
                draftCount = 7,
                mediaCount = 42,
                placeCount = 12,
                locationHistoryCount = 26,
                hasProfile = true,
            ),
    )

private val auditNoteSiblingIds =
    listOf(
        Uuid.parse("00000000-0000-0000-0000-000000000191"),
        Uuid.parse("00000000-0000-0000-0000-000000000192"),
        Uuid.parse("00000000-0000-0000-0000-000000000193"),
    )

private val auditNoteShared =
    NoteViewerShared(
        noteId = auditNoteSiblingIds[1],
        createdAt = ScreenshotTestData.baseInstant,
        lastUpdated = ScreenshotTestData.baseInstant,
        location =
            NoteLocation(
                coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                place =
                    NotePlace(
                        id = Uuid.parse("00000000-0000-0000-0000-000000000194"),
                        name = "Mission District",
                        latitude = 37.7749,
                        longitude = -122.4194,
                    ),
            ),
        journalContext =
            JournalContext(
                journalId = ScreenshotTestData.sampleJournal.id,
                journalTitle = ScreenshotTestData.sampleJournal.title,
                siblingNoteIds = auditNoteSiblingIds,
                currentIndex = 1,
            ),
    )

private val auditAudioContext =
    AudioContext(
        amplitudes = ScreenshotTestData.mockAmplitudes,
        segments =
            listOf(
                AudioSegment(timestampMs = 8_000L, type = SegmentType.SPEECH_ONSET),
                AudioSegment(timestampMs = 34_000L, type = SegmentType.VOLUME_PEAK),
            ),
        daylightPeriod = DaylightPeriod.GOLDEN_HOUR,
        palette =
            AudioPalette(
                waveformGradientStart = 0xFFE8A044,
                waveformGradientEnd = 0xFFD4603A,
                playedFillColor = 0xFFE8A044,
                accentColor = 0xFFE8A044,
                immersiveBackground = 0xFF1A0F05,
            ),
    )

private val auditAudioPlaybackState =
    AudioPlaybackState(
        currentlyPlayingId = Uuid.parse("00000000-0000-0000-0000-000000000195"),
        currentUri = "preview://adaptive-audit/audio",
        isPlaying = true,
        progress = 0.38f,
        duration = 182_000L.milliseconds,
        displayInfo =
            AudioPlaybackDisplayInfo(
                title = "Audio note",
                subtitle = "3:02",
                accentColor = 0xFFE8A044,
            ),
    )

private val auditTimelineDayState =
    TimelineDayUiState(
        summary =
            "Closed the adaptive audit gaps that were still assuming a phone-width canvas, then reviewed " +
                "the foldable screenshots before recording a short follow-up note.",
        date = LocalDate(2025, 2, 20),
        notes =
            listOf(
                TextNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000301"),
                    text =
                        "Timeline detail needs room for a readable daily summary and the mixed notes " +
                            "that explain what happened. Book posture keeps those jobs on separate panes.",
                    timestamp = ScreenshotTestData.baseInstant,
                ),
                ImageNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000302"),
                    uri = SAMPLE_IMAGE_URI,
                    timestamp = ScreenshotTestData.baseInstant - 28.minutes,
                    caption = "Reference capture for the layout pass.",
                ),
                AudioNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000303"),
                    uri = "preview://audio",
                    timestamp = ScreenshotTestData.baseInstant - 48.minutes,
                    duration = 93_000L,
                ),
                VideoNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000304"),
                    uri = SAMPLE_VIDEO_URI,
                    thumbnailUri = SAMPLE_IMAGE_URI,
                    timestamp = ScreenshotTestData.baseInstant - 67.minutes,
                    duration = 61_000L,
                    caption = "Playback review",
                ),
            ),
        events =
            listOf(
                DayEventUiState(
                    eventId = "adaptive-review",
                    title = "Adaptive QA review",
                    description = "Checked the timeline, editor, media, and rewind surfaces against the foldable audit.",
                    start = ScreenshotTestData.baseInstant - 2.hours,
                    end = ScreenshotTestData.baseInstant - 1.hours,
                ),
            ),
        placesVisited =
            listOf(
                PlaceUiState(
                    id = "mission-office",
                    title = "Mission office",
                    latitude = 37.7596,
                    longitude = -122.4269,
                ),
                PlaceUiState(
                    id = "ferry-building",
                    title = "Ferry Building",
                    latitude = 37.7955,
                    longitude = -122.3937,
                ),
            ),
    )

private val auditEditorUiState =
    BlocksUiState(
        blocks =
            listOf(
                TextBlockUiState(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000621"),
                    timestamp = ScreenshotTestData.baseInstant,
                    content =
                        "Drafted a field note with enough text to check reading width, scroll behavior, " +
                            "and the position of editor controls on a folding display.",
                ),
                ImageBlockUiState(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000622"),
                    timestamp = ScreenshotTestData.baseInstant,
                    uri = SAMPLE_IMAGE_URI,
                    caption = "Notebook reference",
                ),
                VideoBlockUiState(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000623"),
                    timestamp = ScreenshotTestData.baseInstant,
                    uri = SAMPLE_VIDEO_URI,
                    caption = "Walkthrough clip",
                    durationMs = 84_000L,
                ),
            ),
        availableJournals = ScreenshotTestData.sampleJournals,
        selectedJournalIds = listOf(ScreenshotTestData.sampleJournal.id),
        onBlockFocused = {},
        onJournalSelectionChanged = {},
        onUpdateBlock = {},
        onCreateBlock = { _: BlockType, id: Uuid -> TextBlockUiState(id = id) },
        onDeleteBlock = {},
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
@Preview(name = "Onboarding start book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A68_OnboardingStartBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            OnboardingStartScreenContent(
                showLanding = true,
                onGetStarted = {},
                onStartFromBackup = {},
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
                onStartFromBackup = {},
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
                useSplitScreen = true,
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
                useSplitScreen = true,
            )
        }
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
            hasPermission = false,
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
                hasPermission = false,
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
                hasPermission = false,
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

@PreviewTest
@Preview(name = "Cloud account welcome book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A98_CloudAccountWelcomeBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            CloudAccountWelcomeContent(
                onContinue = {},
                onSignIn = {},
                onSkip = {},
                serverSelectionState = ServerSelectionState(),
                onSelectServerPreset = {},
                onCustomServerUrlChange = {},
                onShowCustomServerInfo = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cloud account welcome tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A99_CloudAccountWelcomeTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            CloudAccountWelcomeContent(
                onContinue = {},
                onSignIn = {},
                onSkip = {},
                serverSelectionState = ServerSelectionState(),
                onSelectServerPreset = {},
                onCustomServerUrlChange = {},
                onShowCustomServerInfo = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cloud account sign in book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A100_CloudAccountSignInBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            CloudAccountSignInContent(
                username = "alex_j",
                onUsernameChange = {},
                onSignIn = {},
                onAccountRecovery = {},
                onPrivacyPolicy = {},
                onTermsOfService = {},
                onBack = {},
                serverDisplayName = "LogDate Cloud",
                serverHandleDomain = "logdate.app",
                errorMessage = "Invalid username or password.",
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cloud account sign in tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A101_CloudAccountSignInTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            CloudAccountSignInContent(
                username = "alex_j",
                onUsernameChange = {},
                onSignIn = {},
                onAccountRecovery = {},
                onPrivacyPolicy = {},
                onTermsOfService = {},
                onBack = {},
                serverDisplayName = "LogDate Cloud",
                serverHandleDomain = "logdate.app",
                onSignInWithGoogle = {},
                isSigningIn = true,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cloud account creation book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A102_CloudAccountCreationBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            PasskeyAccountCreationFinalContent(
                displayName = "Alex Johnson",
                username = "alex_j",
                bio = "Explorer and photographer",
                onBioChange = {},
                onCreateAccount = {},
                onBack = {},
                isCreatingAccount = false,
                errorMessage = null,
                onClearError = {},
                isPasskeySupported = true,
                handleDomain = "logdate.app",
                serverDisplayName = "LogDate Cloud",
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cloud account creation tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A103_CloudAccountCreationTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            PasskeyAccountCreationFinalContent(
                displayName = "Alex Johnson",
                username = "alex_j",
                bio = "Explorer and photographer",
                onBioChange = {},
                onCreateAccount = {},
                onBack = {},
                isCreatingAccount = true,
                errorMessage = null,
                onClearError = {},
                isPasskeySupported = true,
                handleDomain = "logdate.app",
                serverDisplayName = "LogDate Cloud",
            )
        }
    }
}

@PreviewTest
@Preview(name = "Backup sync book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A104_BackupSyncBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            BackupSyncScreenContent(
                useCompactLayout = false,
                onBack = {},
                onPlanSelected = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Backup sync tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A105_BackupSyncTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            BackupSyncScreenContent(
                useCompactLayout = false,
                onBack = {},
                onPlanSelected = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Recovery phrase setup book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A106_RecoveryPhraseSetupBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            RecoveryPhraseSetupScreen(
                words = listOf(
                    "orbit",
                    "paper",
                    "candle",
                    "forest",
                    "river",
                    "silver",
                    "puzzle",
                    "anchor",
                    "signal",
                    "harbor",
                    "lantern",
                    "sunset",
                ),
                isLoading = false,
                errorMessage = null,
                onRetry = {},
                onPhraseContinue = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Recovery phrase setup tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A107_RecoveryPhraseSetupTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            RecoveryPhraseSetupScreen(
                words = listOf(
                    "orbit",
                    "paper",
                    "candle",
                    "forest",
                    "river",
                    "silver",
                    "puzzle",
                    "anchor",
                    "signal",
                    "harbor",
                    "lantern",
                    "sunset",
                ),
                isLoading = false,
                errorMessage = null,
                onRetry = {},
                onPhraseContinue = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Recovery phrase entry book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A108_RecoveryPhraseEntryBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            RecoveryPhraseEntryScreen(
                onRecoverPhrase = { Result.success(Unit) },
                onRecovered = {},
                onError = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Recovery phrase entry tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A109_RecoveryPhraseEntryTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            RecoveryPhraseEntryScreen(
                onRecoverPhrase = { Result.success(Unit) },
                onRecovered = {},
                onError = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Data settings export book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A110_DataSettingsExportBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            val snackbarHostState = remember { SnackbarHostState() }
            DataSettingsContent(
                onBack = {},
                quotaUsage = auditStorageQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Configuring(options = ExportOptions(), counts = auditExportCounts),
                isExportSheetVisible = true,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = {},
                onBrowseExport = {},
                restoreState = RestoreState.Idle,
                isRestoreSheetVisible = false,
                onShowRestoreSheet = {},
                onSelectRestoreFile = {},
                onUpdateImportOptions = {},
                onConfirmImport = {},
                onCancelRestore = {},
                onRetryRestore = {},
                onDismissRestore = {},
                integrityState = IntegrityState(),
                onRunIntegrityCheck = {},
                onRepairIntegrity = {},
                conflictsState = ConflictsState(),
                onClearConflicts = {},
                onRefreshConflicts = {},
                snackbarHostState = snackbarHostState,
                syncStatus = null,
                isAuthenticated = false,
                onSyncNow = {},
                isBackgroundSyncEnabled = true,
                onBackgroundSyncEnabledChange = {},
                onNavigateToCloudAccountCreation = {},
                onNavigateToSignIn = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Data settings export progress book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A111_DataSettingsExportProgressBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            val snackbarHostState = remember { SnackbarHostState() }
            DataSettingsContent(
                onBack = {},
                quotaUsage = auditStorageQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Exporting(progressPercent = 62, message = "Packaging entries and attachments"),
                isExportSheetVisible = true,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = {},
                onBrowseExport = {},
                restoreState = RestoreState.Idle,
                isRestoreSheetVisible = false,
                onShowRestoreSheet = {},
                onSelectRestoreFile = {},
                onUpdateImportOptions = {},
                onConfirmImport = {},
                onCancelRestore = {},
                onRetryRestore = {},
                onDismissRestore = {},
                integrityState = IntegrityState(),
                onRunIntegrityCheck = {},
                onRepairIntegrity = {},
                conflictsState = ConflictsState(),
                onClearConflicts = {},
                onRefreshConflicts = {},
                snackbarHostState = snackbarHostState,
                syncStatus = null,
                isAuthenticated = false,
                onSyncNow = {},
                isBackgroundSyncEnabled = true,
                onBackgroundSyncEnabledChange = {},
                onNavigateToCloudAccountCreation = {},
                onNavigateToSignIn = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Data settings restore preview book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A112_DataSettingsRestorePreviewBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            val snackbarHostState = remember { SnackbarHostState() }
            DataSettingsContent(
                onBack = {},
                quotaUsage = auditStorageQuota,
                isQuotaAvailable = true,
                exportState = ExportState.Idle,
                isExportSheetVisible = false,
                onShowExportOptions = {},
                onUpdateExportOptions = {},
                onConfirmExport = {},
                onCancelExport = {},
                onRetryExport = {},
                onDismissExport = {},
                onBrowseExport = {},
                restoreState =
                    RestoreState.Previewing(
                        preview = auditArchivePreview,
                        fileName = "logdate-backup.ldz",
                        options = ImportOptions(),
                    ),
                isRestoreSheetVisible = true,
                onShowRestoreSheet = {},
                onSelectRestoreFile = {},
                onUpdateImportOptions = {},
                onConfirmImport = {},
                onCancelRestore = {},
                onRetryRestore = {},
                onDismissRestore = {},
                integrityState = IntegrityState(),
                onRunIntegrityCheck = {},
                onRepairIntegrity = {},
                conflictsState = ConflictsState(),
                onClearConflicts = {},
                onRefreshConflicts = {},
                snackbarHostState = snackbarHostState,
                syncStatus = null,
                isAuthenticated = false,
                onSyncNow = {},
                isBackgroundSyncEnabled = true,
                onBackgroundSyncEnabledChange = {},
                onNavigateToCloudAccountCreation = {},
                onNavigateToSignIn = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Reset settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A113_ResetSettingsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            ResetSettingsScreen(
                onBack = {},
                onNavigateToClearData = {},
                onNavigateToResetApp = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Clear data book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A114_ClearDataSettingsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            ClearDataSettingsContent(
                onBack = {},
                onClearData = { _, _ -> },
            )
        }
    }
}

@PreviewTest
@Preview(name = "Reset app book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A115_ResetAppSettingsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            ResetAppSettingsContent(
                onBack = {},
                onAppReset = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A03_CloudAccountSetup() {
    ScreenshotTheme {
        CloudAccountSetupContent(
            useCompactLayout = false,
            onBack = {},
            onContinue = {},
            onSkip = {},
            onPlanSelected = {},
            selectedOption = null,
            onOptionSelected = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A04_CloudAccountIntro() {
    ScreenshotTheme {
        CloudAccountWelcomeContent(
            onContinue = {},
            onSignIn = {},
            onSkip = {},
            serverSelectionState = ServerSelectionState(),
            onSelectServerPreset = {},
            onCustomServerUrlChange = {},
            onShowCustomServerInfo = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A05_SearchWithResults() {
    ScreenshotTheme {
        SearchScreenContent(
            searchState = SearchScreenState.Results(query = "train", results = auditSearchResults),
            onQueryChange = {},
            onCommitSearch = {},
            onResultClick = {},
            onResultOpenDay = {},
            onGoBack = {},
            queryText = "train",
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
            searchQuery = "audit",
            entryResults = auditSearchResults,
            onOpenJournal = {},
            onBrowseJournals = {},
            onCreateJournal = {},
            onNavigateToDay = {},
            onNavigationClick = {},
            onQueryChange = {},
            onToggleLayoutMode = {},
            onSortOptionSelected = {},
            onToggleFilter = {},
        )
    }
}

@PreviewTest
@Preview(name = "Journals overview book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A40_JournalsOverviewBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalsOverviewScreenContent(
                journals = auditJournals,
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                searchQuery = "audit",
                entryResults = auditSearchResults,
                onOpenJournal = {},
                onBrowseJournals = {},
                onCreateJournal = {},
                onNavigateToDay = {},
                onNavigationClick = {},
                onQueryChange = {},
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Empty journals book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A42_EmptyJournalsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalsOverviewScreenContent(
                journals = emptyList(),
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                searchQuery = "",
                entryResults = emptyList(),
                onOpenJournal = {},
                onBrowseJournals = {},
                onCreateJournal = {},
                onNavigateToDay = {},
                onNavigationClick = {},
                onQueryChange = {},
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A43_EmptyJournalsResponsiveStates() {
    ScreenshotTheme {
        JournalsOverviewScreenContent(
            journals = emptyList(),
            layoutMode = JournalLayoutMode.CAROUSEL,
            sortOption = JournalSortOption.LAST_UPDATED,
            activeFilters = emptySet(),
            searchQuery = "",
            entryResults = emptyList(),
            onOpenJournal = {},
            onBrowseJournals = {},
            onCreateJournal = {},
            onNavigateToDay = {},
            onNavigationClick = {},
            onQueryChange = {},
            onToggleLayoutMode = {},
            onSortOptionSelected = {},
            onToggleFilter = {},
        )
    }
}

@PreviewTest
@Preview(name = "Journal creation book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A44_JournalCreationBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalCreationScreenContent(
                onGoBack = {},
                onNewJournal = {},
                initialTitle = "Route Screenshot Rollout",
                selectedNoteIds = setOf(Uuid.parse("00000000-0000-0000-0000-000000000141")),
                selectedMediaUris = listOf("content://logdate/audit-cover.jpg"),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Journal settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A45_JournalSettingsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalSettingsScreenContent(
                uiState =
                    JournalSettingsUiState.Loaded(
                        journal = ScreenshotTestData.sampleJournal,
                        editedName = "Route Screenshot Rollout",
                        editedDescription = "A journal used to validate foldable settings flows.",
                        hasUnsavedChanges = true,
                    ),
                onGoBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Journal settings delete book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A46_JournalSettingsDeleteBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalSettingsScreenContent(
                uiState =
                    JournalSettingsUiState.Loaded(
                        journal = ScreenshotTestData.sampleJournal,
                        editedName = ScreenshotTestData.sampleJournal.title,
                        editedDescription = "A journal used to validate foldable settings flows.",
                    ),
                onGoBack = {},
                showDeleteConfirmation = true,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Share journal book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A47_ShareJournalBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
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
}

@PreviewTest
@Preview(name = "Note viewer text book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A48_NoteViewerTextBookPosture() {
    BookPostureNoteViewerScene {
        Text(
            text =
                "Captured the train ride home before the details blurred. The city felt quieter than usual, " +
                    "so I wrote down the stops, the light through the windows, and the idea for tomorrow's entry.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@PreviewTest
@Preview(name = "Note viewer image book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A49_NoteViewerImageBookPosture() {
    BookPostureNoteViewerScene {
        AuditNoteImage()
    }
}

@PreviewTest
@Preview(name = "Note viewer video book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A50_NoteViewerVideoBookPosture() {
    BookPostureNoteViewerScene {
        AuditNoteVideo()
    }
}

@PreviewTest
@Preview(name = "Note viewer audio book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A51_NoteViewerAudioBookPosture() {
    FoldableAudioNoteViewerScene(bookPostureLayoutInfo)
}

@PreviewTest
@Preview(name = "Note viewer text tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A52_NoteViewerTextTabletopPosture() {
    TabletopPostureNoteViewerScene {
        Text(
            text =
                "Captured the train ride home before the details blurred. The top pane keeps the note readable " +
                    "while the lower pane keeps navigation and journal actions reachable.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@PreviewTest
@Preview(name = "Note viewer image tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A53_NoteViewerImageTabletopPosture() {
    TabletopPostureNoteViewerScene {
        AuditNoteImage()
    }
}

@PreviewTest
@Preview(name = "Note viewer video tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A54_NoteViewerVideoTabletopPosture() {
    TabletopPostureNoteViewerScene {
        AuditNoteVideo()
    }
}

@PreviewTest
@Preview(name = "Note viewer audio tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A55_NoteViewerAudioTabletopPosture() {
    FoldableAudioNoteViewerScene(tabletopPostureLayoutInfo)
}

@PreviewTest
@Preview(name = "Media detail image book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A56_MediaDetailImageBookPosture() {
    FoldableMediaDetailScene(
        foldableLayoutInfo = bookPostureLayoutInfo,
        state = LibraryScreenshotData.imageDetail.copy(mediaRef = SAMPLE_IMAGE_URI),
    )
}

@PreviewTest
@Preview(name = "Media detail video book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A57_MediaDetailVideoBookPosture() {
    FoldableMediaDetailScene(
        foldableLayoutInfo = bookPostureLayoutInfo,
        state = LibraryScreenshotData.videoDetail.copy(mediaRef = SAMPLE_VIDEO_URI),
    )
}

@PreviewTest
@Preview(name = "Media detail image tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A58_MediaDetailImageTabletopPosture() {
    FoldableMediaDetailScene(
        foldableLayoutInfo = tabletopPostureLayoutInfo,
        state = LibraryScreenshotData.imageDetail.copy(mediaRef = SAMPLE_IMAGE_URI),
    )
}

@PreviewTest
@Preview(name = "Media detail video tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A59_MediaDetailVideoTabletopPosture() {
    FoldableMediaDetailScene(
        foldableLayoutInfo = tabletopPostureLayoutInfo,
        state = LibraryScreenshotData.videoDetail.copy(mediaRef = SAMPLE_VIDEO_URI),
    )
}

@PreviewTest
@Preview(name = "Audio recording controls tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A60_AudioRecordingControlsTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            AudioRecordingControls(
                recordingState = RecordingState.RECORDING,
                audioLevels = ScreenshotTestData.mockAudioLevels,
                recordingDuration = 1.minutes + 23.seconds,
                onStartRecording = {},
                onStopRecording = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Active recording tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A61_ActiveRecordingTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            ActiveRecordingDisplay(
                audioLevels = ScreenshotTestData.mockAudioLevels,
                recordingDuration = 2.minutes + 15.seconds,
                onRestart = {},
                onPause = {},
                onFinish = {},
                transcriptionText =
                    "Started capturing notes for the adaptive audit. The active recording surface keeps the " +
                        "live transcript readable while recording controls remain in the reachable lower pane.",
                transcriptionIsRefining = true,
                isPaused = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Entry editor mixed content book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A62_EntryEditorMixedContentBookPosture() {
    FoldableEntryEditorScene(bookPostureLayoutInfo)
}

@PreviewTest
@Preview(name = "Entry editor mixed content tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A63_EntryEditorMixedContentTabletopPosture() {
    FoldableEntryEditorScene(tabletopPostureLayoutInfo)
}

@PreviewTest
@Preview(name = "Audio transcript book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A64_AudioTranscriptBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            AudioTranscriptionUi(
                transcriptionState =
                    AudioUiState.TranscriptionState.Success(
                        text =
                            "We walked through the adaptive audit and captured the places where the " +
                                "editor still assumed a phone-sized canvas. The transcript reader keeps " +
                                "long-form generated text constrained to a readable pane while status " +
                                "context stays on the adjacent side of the fold.",
                        isRefining = true,
                    ),
                onRequestTranscription = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timeline day detail book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A65_TimelineDayDetailBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            TimelineDayDetailPanel(
                uiState = auditTimelineDayState,
                onExit = {},
                onOpenEvent = {},
                onOpenLocations = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Timeline day detail tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A66_TimelineDayDetailTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            TimelineDayDetailPanel(
                uiState = auditTimelineDayState,
                onExit = {},
                onOpenEvent = {},
                onOpenLocations = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
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

@Composable
private fun FoldableMediaDetailScene(
    foldableLayoutInfo: FoldableLayoutInfo,
    state: MediaDetailUiState,
) {
    provideFoldableLayoutInfo(foldableLayoutInfo) {
        ScreenshotTheme {
            MediaDetailContent(
                state = state,
                isExpanded = true,
                onBack = {},
            )
        }
    }
}

@Composable
private fun FoldableEntryEditorScene(foldableLayoutInfo: FoldableLayoutInfo) {
    provideFoldableLayoutInfo(foldableLayoutInfo) {
        ScreenshotTheme {
            ImmersiveEditorLayout(
                topBarContent = {
                    NoteEditorToolbar(
                        onBack = {},
                        onSave = {},
                        onShowDrafts = {},
                    )
                },
                editorContent = {
                    MainEditorContent(
                        uiState = auditEditorUiState,
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                bottomContent = {
                    EditorBottomContent(
                        availableJournals = ScreenshotTestData.sampleJournals,
                        selectedJournalIds = listOf(ScreenshotTestData.sampleJournal.id),
                        onJournalSelectionChanged = {},
                        journalSelectorExpanded = false,
                        onJournalSelectorExpandedChange = {},
                    )
                },
            )
        }
    }
}

@Composable
private fun BookPostureNoteViewerScene(noteContent: @Composable () -> Unit) {
    FoldableNoteViewerScene(
        foldableLayoutInfo = bookPostureLayoutInfo,
        noteContent = noteContent,
    )
}

@Composable
private fun TabletopPostureNoteViewerScene(noteContent: @Composable () -> Unit) {
    FoldableNoteViewerScene(
        foldableLayoutInfo = tabletopPostureLayoutInfo,
        noteContent = noteContent,
    )
}

@Composable
private fun FoldableNoteViewerScene(
    foldableLayoutInfo: FoldableLayoutInfo,
    noteContent: @Composable () -> Unit,
) {
    provideFoldableLayoutInfo(foldableLayoutInfo) {
        ScreenshotTheme {
            NoteViewerScaffoldContent(
                shared = auditNoteShared,
                onGoBack = {},
                noteContent = noteContent,
            )
        }
    }
}

@Composable
private fun AuditNoteImage() {
    Image(
        painter = painterResource(R.drawable.sample_note_photo),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun AuditNoteVideo() {
    VideoPlayerContent(
        uri = SAMPLE_VIDEO_URI,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
    )
}

@Composable
private fun FoldableAudioNoteViewerScene(foldableLayoutInfo: FoldableLayoutInfo) {
    provideFoldableLayoutInfo(foldableLayoutInfo) {
        ScreenshotTheme {
            CompositionLocalProvider(LocalAudioPlaybackState provides auditAudioPlaybackState) {
                AudioNoteViewerContent(
                    uiState =
                        AudioNoteViewerUiState.Ready(
                            mediaRef = "preview://adaptive-audit/audio",
                            durationMs = 182_000L,
                            createdAt = ScreenshotTestData.baseInstant,
                            context = auditAudioContext,
                            playbackState = AudioPlaybackUiState(progress = 0.38f, isPlaying = true),
                        ),
                    onGoBack = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Journal detail book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A41_JournalDetailBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            JournalDetailScreenContent(
                uiState = auditJournalState,
                onGoBack = {},
            )
        }
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

@PreviewTest
@Preview(name = "Profile edit book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A67_ProfileEditBookPosture() {
    val snackbarHostState = remember { SnackbarHostState() }

    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            ProfileScreenContent(
                uiState =
                    ProfileUiState(
                        localProfile = auditProfile,
                        account = auditAccount,
                        userData = null,
                        editState = ProfileEditState.DisplayName("Alex Johnson"),
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
}
