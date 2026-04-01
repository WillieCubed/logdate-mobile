package app.logdate.screenshots.flows.flow00_app_entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.client.database.DatabaseStartupState
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.screenshots.common.HomeTabRouteFrame
import app.logdate.screenshots.common.RoutePreviewTab
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.createTimelineDayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.encrypted_data_recovery_required
import logdate.app.composemain.generated.resources.open_recovery_tools
import logdate.app.composemain.generated.resources.reset_encrypted_storage
import logdate.app.composemain.generated.resources.restart
import logdate.app.composemain.generated.resources.update_ready_restart_to_finish_installing
import org.jetbrains.compose.resources.stringResource

private val rootTimelineDays =
    listOf(
        createTimelineDayUiState(
            summary = "Wrapped up the screenshot audit and tightened the adaptive shells for larger windows.",
            date = LocalDate(2025, 2, 20),
            notes =
                listOf(
                    ImageNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000061"),
                        uri = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher",
                        timestamp = Instant.parse("2025-02-20T18:10:00Z"),
                    ),
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000062"),
                        text = "Tightened the adaptive timeline shells for larger windows and made the cards feel intentional.",
                        timestamp = Instant.parse("2025-02-20T18:25:00Z"),
                    ),
                ),
            placesVisited =
                listOf(
                    PlaceUiState(id = "place-1", title = "Blue Bottle Coffee"),
                    PlaceUiState(id = "place-2", title = "Mission Dolores Park"),
                ),
        ),
        createTimelineDayUiState(
            summary = "Reviewed the journals flow on tablet and trimmed the empty padding back to a readable column.",
            date = LocalDate(2025, 2, 19),
            notes =
                listOf(
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000063"),
                        text = "The tablet journals flow finally reads like a deliberate column instead of a stretched phone screen.",
                        timestamp = Instant.parse("2025-02-19T20:00:00Z"),
                    ),
                ),
            placesVisited = listOf(PlaceUiState(id = "place-3", title = "Home")),
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_MainActivityRootDatabaseRecovery() {
    ScreenshotTheme {
        RootPreviewFrame(
            databaseStartupState =
                DatabaseStartupState.RecoveryRequired(
                    reason = "Encrypted database could not be decrypted with the current key.",
                    detail = "SQLiteException: file is not a database",
                ),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_MainActivityRootHomeLoaded() {
    ScreenshotTheme {
        RootPreviewFrame()
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_MainActivityRootUpdateReadySnackbar() {
    ScreenshotTheme {
        RootPreviewFrame(
            appUpdateUiState =
                AppUpdateUiState(
                    status = AppUpdateStatus.Downloaded,
                    currentVersionName = "0.1.0",
                ),
        )
    }
}

@Composable
private fun RootPreviewFrame(
    databaseStartupState: DatabaseStartupState = DatabaseStartupState.Ready,
    appUpdateUiState: AppUpdateUiState = AppUpdateUiState(),
) {
    val appUpdateSnackbarHostState = remember { SnackbarHostState() }
    val updateReadyMessage = stringResource(Res.string.update_ready_restart_to_finish_installing)
    val restartLabel = stringResource(Res.string.restart)

    Box(modifier = Modifier.fillMaxSize()) {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = rootTimelineDays),
                onNewEntry = {},
                onShareMemory = { _ -> },
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                timelineSuggestion =
                    TimelineSuggestionBlock.CompleteDraft(
                        draftId = "draft-1",
                    ),
            )
        }

        SnackbarHost(
            hostState = appUpdateSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (databaseStartupState is DatabaseStartupState.RecoveryRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(Res.string.encrypted_data_recovery_required)) },
            text = {
                Text(
                    "LogDate could not open your encrypted local database.\n\n" +
                        "Reason: ${databaseStartupState.reason}\n\n" +
                        "No local data has been deleted automatically. " +
                        "Open recovery tools first to import/restore a backup.",
                )
            },
            confirmButton = {
                TextButton(onClick = {}) {
                    Text(stringResource(Res.string.open_recovery_tools))
                }
            },
            dismissButton = {
                TextButton(onClick = {}) {
                    Text(stringResource(Res.string.reset_encrypted_storage))
                }
            },
        )
    }

    LaunchedEffect(appUpdateUiState.status) {
        if (appUpdateUiState.status != AppUpdateStatus.Downloaded) {
            appUpdateSnackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }

        appUpdateSnackbarHostState.showSnackbar(
            message = updateReadyMessage,
            actionLabel = restartLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
    }
}
