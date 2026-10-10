@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package app.logdate.screenshots.audit.workspace

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalLayoutMode
import app.logdate.feature.journals.ui.JournalListItemUiState
import app.logdate.feature.journals.ui.JournalSortOption
import app.logdate.feature.journals.ui.JournalsOverviewScreenContent
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.journals.ui.detail.NoteViewerScaffoldContent
import app.logdate.feature.journals.ui.detail.NoteViewerShared
import app.logdate.feature.journals.ui.detail.TextNoteViewerContent
import app.logdate.feature.library.ui.LibraryScreenContent
import app.logdate.feature.library.ui.detail.MediaDetailContent
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryActions
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryContent
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.components.home_timeline.HistoryMapFixture
import app.logdate.screenshots.components.home_timeline.historyDay
import app.logdate.screenshots.components.library.LibraryScreenshotData
import app.logdate.screenshots.flows.flow02_home_timeline.mostRecentRewind
import app.logdate.screenshots.flows.flow02_home_timeline.pastRewinds
import app.logdate.screenshots.flows.flow02_home_timeline.timelineDays
import app.logdate.screenshots.flows.flow02_home_timeline.timelineDetailState
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.workspace.AdaptiveWorkspaceLayout
import app.logdate.ui.workspace.WorkspaceDestination
import app.logdate.ui.workspace.WorkspaceScaffold
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid
import app.logdate.ui.workspace.WorkspaceCaptionBar
import app.logdate.ui.workspace.LocalWorkspaceCaptionBar
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column

@Preview(name = "Phone", device = ScreenshotTestData.PHONE, showBackground = true)
@Preview(name = "Compact", device = ScreenshotTestData.COMPACT_PHONE, showBackground = true)
@Preview(
    name = "Dark",
    device = ScreenshotTestData.PHONE,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Large text", device = ScreenshotTestData.PHONE, fontScale = 2f, showBackground = true)
@Preview(name = "Landscape", device = ScreenshotTestData.PHONE_LANDSCAPE, showBackground = true)
@Preview(name = "Tablet", device = ScreenshotTestData.TABLET, showBackground = true)
@Preview(name = "Tablet portrait", device = ScreenshotTestData.TABLET_PORTRAIT, showBackground = true)
@Preview(name = "Wide RTL", device = ScreenshotTestData.TABLET, locale = "ar", showBackground = true)
@Preview(name = "RTL", device = ScreenshotTestData.PHONE, locale = "ar", showBackground = true)
@Preview(name = "Bubble", device = ScreenshotTestData.BUBBLE, showBackground = true)
annotation class WorkspacePreviewMatrix

internal val workspaceDestinations =
    listOf(
        WorkspaceDestination("timeline", "Timeline", Icons.Default.Timeline),
        WorkspaceDestination("locations", "Places", Icons.Default.Place),
        WorkspaceDestination("journals", "Journals", Icons.Default.Book),
        WorkspaceDestination("library", "Library", Icons.Default.Image),
        WorkspaceDestination("rewind", "Rewind", Icons.Default.History),
    )

@Composable
private fun PlacesOptionsFixture() {
    IconButton(onClick = {}) { Icon(PlatformIcons.more(), "More place options", Modifier.size(20.dp)) }
}

@Composable
private fun WorkspaceScene(
    key: String,
    detail: (@Composable () -> Unit)? = null,
    inDesktopWindow: Boolean = false,
    content: @Composable () -> Unit,
) {
    ScreenshotTheme {
        if (inDesktopWindow) {
            DesktopWindow { WorkspaceSceneContent(key, detail, content) }
        } else {
            WorkspaceSceneContent(key, detail, content)
        }
    }
}

@Composable
private fun WorkspaceSceneContent(
    key: String,
    detail: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val image =
        remember(
            context,
        ) { BitmapFactory.decodeResource(context.resources, app.logdate.client.R.drawable.workspace_sample_photo).asImage() }
    val handler = remember(image) { AsyncImagePreviewHandler { image } }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides handler) {
        WorkspaceScaffold(workspaceDestinations, key, {}, onCreate = {}.takeIf { key != "locations" }, onSearch = {}, actions = {
            app.logdate.feature.core.main.HomeWorkspaceAccountAction(
                app.logdate.feature.core.sync.SyncPresentation
                    .Pending(2),
                app.logdate.ui.streak
                    .CampfirePresentation(app.logdate.ui.streak.CampfirePhase.BURNING, runDays = 4),
                {},
                {},
                {},
            )
        }) {
            if (key == "locations" && detail == null) {
                content()
            } else {
                AdaptiveWorkspaceLayout(
                    Modifier.fillMaxSize(),
                    browseOnStart = true,
                    focusConstraints =
                        if (key == "timeline" && detail == null) {
                            app.logdate.ui.workspace.PanelConstraints.ReadingCollection
                        } else if (key ==
                            "library"
                        ) {
                            app.logdate.ui.workspace.PanelConstraints.Visual
                        } else {
                            app.logdate.ui.workspace.PanelConstraints.Reading
                        },
                    browse = content.takeIf { detail != null },
                    focus =
                        detail ?: content,
                )
            }
        }
    }
}

@Composable private fun TimelineFixture() = TimelinePane(TimelineUiState(timelineDays), {}, {})

@Composable private fun JournalsFixture() =
    JournalsOverviewScreenContent(
        journals = ScreenshotTestData.sampleJournals.map(JournalListItemUiState::ExistingJournal),
        layoutMode = JournalLayoutMode.GRID,
        sortOption = JournalSortOption.LAST_UPDATED,
        activeFilters = emptySet(),
        searchQuery = "",
        entryResults = emptyList(),
        onOpenJournal = {},
        onBrowseJournals = {},
        onCreateJournal = {},
        onNavigateToDay = {},
        onQueryChange = {},
        onToggleLayoutMode = {},
        onSortOptionSelected = {},
        onToggleFilter = {},
    )

@Composable private fun LibraryFixture() =
    BoxWithConstraints {
        LibraryScreenContent(
            LibraryScreenshotData.gridContent,
            (maxWidth / 140.dp).toInt().coerceAtLeast(2),
            {},
            modifier = Modifier.fillMaxSize(),
        )
    }

@Composable private fun RewindFixture() {
    CompositionLocalProvider(app.logdate.ui.platform.LocalReduceMotionOverride provides true) {
        RewindScreenContent(
            RewindOverviewScreenUiState.Ready(
                mostRecentRewind =
                    mostRecentRewind.copy(
                        heroImageUri = "workspace://week-photo",
                        isViewed = false,
                        highlightedQuote = "I finally made a little room to slow down.",
                    ),
                pastRewinds = pastRewinds,
            ),
            {
            },
        )
    }
}

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceTimeline() = WorkspaceScene("timeline") { TimelineFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceLocations() =
    WorkspaceScene("locations") {
        HumanLocationHistoryContent(historyDay, HumanLocationHistoryActions(), showTitle = false, toolbarActions = {
            PlacesOptionsFixture()
        }, mapContent = { HistoryMapFixture(it) })
    }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceJournals() = WorkspaceScene("journals") { JournalsFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceLibrary() = WorkspaceScene("library") { LibraryFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceRewind() = WorkspaceScene("rewind") { RewindFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceDayDetail() =
    WorkspaceScene(
        "timeline",
        detail = { TimelineDayDetailPanel(timelineDetailState, {}) },
    ) { TimelineFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceVisitDetail() =
    WorkspaceScene("locations") {
        HumanLocationHistoryContent(
            historyDay.copy(detailVisible = true),
            HumanLocationHistoryActions(),
            showTitle = false,
            toolbarActions = { PlacesOptionsFixture() },
            mapContent = { HistoryMapFixture(it) },
        )
    }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceJournalDetail() =
    WorkspaceScene("journals", detail = {
        JournalDetailScreenContent(
            uiState =
                JournalDetailUiState.Success(
                    ScreenshotTestData.sampleJournal.id,
                    "Everyday moments",
                    listOf(
                        EntryDisplayData.TextEntry(
                            Uuid.parse("00000000-0000-0000-0000-000000000031"),
                            ScreenshotTestData.baseInstant,
                            "A quiet table, a good book, and nowhere to rush.",
                        ),
                    ),
                ),
            onGoBack = {},
            onOpenEditor = {},
            onNavigateToShare = {},
            onNavigateToSettings = {},
            onNavigateToNoteDetail = {},
            onToggleSortOrder = {},
            onRequestDelete = {},
        )
    }) { JournalsFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceMediaDetail() =
    WorkspaceScene("library", detail = {
        MediaDetailContent(LibraryScreenshotData.imageDetail, isExpanded = true, onBack = {})
    }) { LibraryFixture() }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceNoteDetail() =
    WorkspaceScene("timeline", detail = {
        val shared =
            NoteViewerShared(
                Uuid.parse("00000000-0000-0000-0000-000000000031"),
                ScreenshotTestData.baseInstant,
                ScreenshotTestData.baseInstant,
                null,
            )
        NoteViewerScaffoldContent(shared, {}) { TextNoteViewerContent("A quiet table, a good book, and nowhere to rush.", shared) }
    }) { TimelineFixture() }

@PreviewTest
@Preview(name = "Book", device = ScreenshotTestData.TABLET, showBackground = true)
@Composable
fun WorkspaceBook() {
    app.logdate.ui.foldable.provideFoldableLayoutInfo(workspaceFold(true)) {
        WorkspaceScene("timeline", detail = { TimelineDayDetailPanel(timelineDetailState, {}) }) { TimelineFixture() }
    }
}

@PreviewTest
@Preview(name = "Tabletop", device = ScreenshotTestData.TABLET_PORTRAIT, showBackground = true)
@Composable
fun WorkspaceTabletop() {
    app.logdate.ui.foldable.provideFoldableLayoutInfo(workspaceFold(false)) {
        WorkspaceScene("locations") {
            HumanLocationHistoryContent(
                historyDay,
                HumanLocationHistoryActions(),
                showTitle = false,
                toolbarActions = { PlacesOptionsFixture() },
                mapContent = { HistoryMapFixture(it) },
            )
        }
    }
}

private fun workspaceFold(book: Boolean): app.logdate.ui.foldable.FoldableLayoutInfo {
    val bounds =
        if (book) {
            app.logdate.ui.foldable
                .FoldableHingeBounds(630.dp, 0.dp, 650.dp, 800.dp, 20.dp, 800.dp)
        } else {
            app.logdate.ui.foldable
                .FoldableHingeBounds(0.dp, 630.dp, 800.dp, 650.dp, 800.dp, 20.dp)
        }
    return app.logdate.ui.foldable.FoldableLayoutInfo(
        true,
        if (book) app.logdate.ui.foldable.FoldablePosture.Book else app.logdate.ui.foldable.FoldablePosture.Tabletop,
        app.logdate.ui.foldable.FoldableHingeInfo(
            if (book) {
                app.logdate.ui.foldable.FoldableHingeOrientation.Vertical
            } else {
                app.logdate.ui.foldable.FoldableHingeOrientation.Horizontal
            },
            app.logdate.ui.foldable.FoldableHingeState.HalfOpened,
            app.logdate.ui.foldable.FoldableOcclusionType.Full,
            bounds,
            true,
        ),
    )
}

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspacePlaces() =
    WorkspaceScene("locations") {
        HumanLocationHistoryContent(
            historyDay.copy(tab = app.logdate.feature.location.timeline.ui.history.HistoryTab.Places),
            HumanLocationHistoryActions(),
            showTitle = false,
            toolbarActions = { PlacesOptionsFixture() },
            initialSupportingExtent = app.logdate.ui.workspace.SupportingExtent.Browsing,
            mapContent = {
                HistoryMapFixture(it)
            },
        )
    }

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceRewindPlayback() = ImmersiveRewindScreenshot()

@PreviewTest @WorkspacePreviewMatrix @Composable
fun WorkspaceLocationsBrowsing() =
    WorkspaceScene("locations") {
        HumanLocationHistoryContent(
            historyDay,
            HumanLocationHistoryActions(),
            showTitle = false,
            initialSupportingExtent = app.logdate.ui.workspace.SupportingExtent.Browsing,
            toolbarActions = { PlacesOptionsFixture() },
            mapContent = { HistoryMapFixture(it) },
        )
    }

@PreviewTest
@Preview(name = "Reduced motion", device = ScreenshotTestData.PHONE, showBackground = true)
@Composable
fun WorkspaceReducedMotion() {
    CompositionLocalProvider(app.logdate.ui.platform.LocalReduceMotionOverride provides true) {
        WorkspaceScene("locations") {
            HumanLocationHistoryContent(
                historyDay,
                HumanLocationHistoryActions(),
                showTitle = false,
                toolbarActions = { PlacesOptionsFixture() },
                mapContent = { HistoryMapFixture(it) },
            )
        }
    }
}

@PreviewTest
@Preview(name = "Sync recovery", device = ScreenshotTestData.COMPACT_PHONE, fontScale = 2f, showBackground = true)
@Composable
fun WorkspaceSyncRecovery() =
    ScreenshotTheme {
        WorkspaceScaffold(workspaceDestinations, "locations", {}, onSearch = {}, actions = {
            app.logdate.feature.core.main.HomeWorkspaceAccountAction(
                app.logdate.feature.core.sync.SyncPresentation.NeedsRecovery,
                null,
                {},
                {},
                {},
            )
        }) {
            HumanLocationHistoryContent(
                historyDay.copy(
                    recoveryMessage = "Recording was interrupted",
                    recoveryActionLabel = "Resume recording",
                ),
                HumanLocationHistoryActions(),
                showTitle = false,
                toolbarActions = { PlacesOptionsFixture() },
                mapContent = {
                    HistoryMapFixture(it)
                },
            )
        }
    }

@Preview(name = "Desktop window", device = ScreenshotTestData.TABLET, showBackground = true)
@Preview(
    name = "Desktop window dark",
    device = ScreenshotTestData.TABLET,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Narrow desktop window", device = "spec:width=720dp,height=600dp", showBackground = true)
@Preview(name = "Desktop window RTL", device = ScreenshotTestData.TABLET, locale = "ar", showBackground = true)
@Preview(name = "Desktop window large text", device = ScreenshotTestData.TABLET, fontScale = 2f, showBackground = true)
annotation class DesktopWindowPreviewMatrix

/**
 * Home in an Android desktop window, with the app bar in the window's caption bar. Android draws the
 * caption's app menu and window buttons itself; [SystemCaptionControls] stands in for them so the
 * references show what the app bar avoids. In right-to-left the system swaps their sides.
 */
@Composable
private fun DesktopWindow(content: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val appMenu = 160.dp
    val windowButtons = 144.dp
    val captionBar =
        if (rtl) {
            WorkspaceCaptionBar(height = 48.dp, left = windowButtons, right = appMenu)
        } else {
            WorkspaceCaptionBar(height = 48.dp, left = appMenu, right = windowButtons)
        }
    CompositionLocalProvider(LocalWorkspaceCaptionBar provides captionBar) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            // Large text keeps the app bar out of the caption bar. On a device the window's caption
            // inset then places the workspace below the bar; previews have no insets, so the
            // stand-in bar is laid out above it.
            Column(Modifier.fillMaxSize()) {
                SystemCaptionControls(captionBar.height, appMenu, windowButtons)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                content()
                SystemCaptionControls(captionBar.height, appMenu, windowButtons)
            }
        }
    }
}

@Composable
private fun SystemCaptionControls(
    height: androidx.compose.ui.unit.Dp,
    appMenuWidth: androidx.compose.ui.unit.Dp,
    windowButtonsWidth: androidx.compose.ui.unit.Dp,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(appMenuWidth).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text("LogDate", color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            Icon(Icons.Default.ExpandMore, null, tint = color)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(windowButtonsWidth), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Icons.Default.Remove, Icons.Default.CropSquare, Icons.Default.Close).forEach { Icon(it, null, tint = color) }
        }
    }
}

@PreviewTest @DesktopWindowPreviewMatrix @Composable
fun WorkspaceTimelineInDesktopWindow() = WorkspaceScene("timeline", inDesktopWindow = true) { TimelineFixture() }

@PreviewTest @DesktopWindowPreviewMatrix @Composable
fun WorkspaceLocationsInDesktopWindow() =
    WorkspaceScene("locations", inDesktopWindow = true) {
        HumanLocationHistoryContent(historyDay, HumanLocationHistoryActions(), showTitle = false, toolbarActions = {
            PlacesOptionsFixture()
        }, mapContent = { HistoryMapFixture(it) })
    }
