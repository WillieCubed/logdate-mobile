package app.logdate.client.e2e

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.ArchivePreview
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreStage
import app.logdate.feature.core.restore.RestoreState
import app.logdate.feature.core.restore.RestoreSummary
import app.logdate.feature.core.settings.ui.ExportSettingsContent
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Real Android sheets, accessibility actions, and rendered states at host-selected display sizes. */
class RestoreStateAccessibilityRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun partialRecoveryNeverClaimsCompletionAndActionsRemainReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run through recovery UI acceptance", args.getString("recoveryUiAcceptance") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(instrumentation.targetContext.packageName.endsWith(".debug"))
        val label = requireNotNull(args.getString("recoveryUiLabel"))
        require(label.matches(Regex("[a-z0-9-]+")))
        val fontScale = args.getString("recoveryUiFontScale")?.toFloat() ?: 1f
        require(fontScale in setOf(1f, 2f))
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "recovery-state-acceptance/$label")
        check(output.mkdirs())
        val summary =
            RestoreSummary(
                source = "Journal archive.zip",
                journalsImported = 2,
                notesImported = 12,
                draftsImported = 1,
                journalLinksImported = 12,
                mediaImported = 3,
                recoveryId = "synthetic-recovery",
            )
        val scenes =
            listOf(
                Scene(
                    "preview",
                    RestoreState.Previewing(
                        ArchivePreview(ExportSchemaVersion.CURRENT, Instant.parse("2026-10-15T12:00:00Z"), "1.0", ExportStats(2, 12, 1, 3)),
                        "Journal archive.zip",
                    ),
                    "Import options",
                    "Import",
                ),
                Scene("active", RestoreState.Restoring(RestoreStage.IMPORTING_MEDIA, 37), "Importing…", "Cancel"),
                Scene(
                    "recovering",
                    RestoreState.Completed(
                        summary.copy(
                            unavailableOriginals = 2,
                            recoveringMedia = true,
                            mediaRecoveryChecked = 1,
                            mediaRecoveryTotal = 3,
                        ),
                    ),
                    "Recovering files",
                    "Done",
                ),
                Scene("partial", RestoreState.Completed(summary.copy(unavailableOriginals = 2)), "Entries restored", "Choose archive"),
                Scene(
                    "paused",
                    RestoreState.Completed(summary.copy(metadataIncomplete = true, canResume = true)),
                    "Import paused",
                    "Resume import",
                ),
                Scene("interrupted", RestoreState.Completed(summary.copy(metadataIncomplete = true)), "Finish importing", "Choose archive"),
                Scene("complete", RestoreState.Completed(summary), "Import complete", "Done"),
            )
        val scene = mutableStateOf(scenes.first())
        val clicks = mutableListOf<String>()
        compose.setContent {
            LogDateTheme(dynamicColor = false) {
                ExportSettingsContent(
                    onBack = {},
                    exportState = ExportState.Idle,
                    onShowExportOptions = {},
                    onUpdateExportOptions = {},
                    onConfirmExport = {},
                    onCancelExport = {},
                    onRetryExport = {},
                    onDismissExport = {},
                    onBrowseExport = {},
                    restoreState = scene.value.state,
                    onShowRestoreSheet = {},
                    onSelectRestoreFile = {},
                    onUpdateImportOptions = {},
                    onConfirmImport = { clicks += "Import" },
                    onCancelRestore = { clicks += "Cancel" },
                    onRetryRestore = { clicks += scene.value.action },
                    onDismissRestore = { clicks += "Done" },
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        val device = UiDevice.getInstance(instrumentation)
        scenes.forEach { next ->
            compose.runOnIdle { scene.value = next }
            compose.waitForIdle()
            compose.onNodeWithText(next.title).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(next.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layout = mutableListOf<TextLayoutResult>()
                assertTrue(action(layout))
                assertEquals(
                    fontScale,
                    layout
                        .single()
                        .layoutInput.density.fontScale,
                    "The modal sheet must use Android's font scale",
                )
            }
            if (next.name != "complete") compose.onNodeWithText("Import complete").assertDoesNotExist()
            if (next.name == "recovering") {
                compose.onNodeWithText("Checked 1 of 3 media items.").performScrollTo().assertIsDisplayed()
            }
            assertTrue(device.takeScreenshot(File(output, "${next.name}-content.png")))
            compose
                .onNodeWithText(next.action)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
            assertTrue(device.takeScreenshot(File(output, "${next.name}-action.png")))
            val before = clicks.size
            compose.onNodeWithText(next.action).performClick()
            compose.runOnIdle {
                assertEquals(before + 1, clicks.size)
                assertEquals(next.action, clicks.last())
            }
        }
    }

    private data class Scene(
        val name: String,
        val state: RestoreState,
        val title: String,
        val action: String,
    )
}
