package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DefaultCaptureModeSettingsTest {
    @Test
    fun memoriesSettingsOwnsOneCapturePreferenceOnPhoneAndBook() {
        for (book in listOf(false, true)) {
            runDesktopComposeUiTest(width = if (book) 1440 else 411, height = 1200) {
                setContent {
                    provideFoldableLayoutInfo(
                        if (book) {
                            FoldableLayoutInfo(
                                isFoldable = true,
                                posture = FoldablePosture.Book,
                                hinge =
                                    FoldableHingeInfo(
                                        FoldableHingeOrientation.Vertical,
                                        FoldableHingeState.HalfOpened,
                                        FoldableOcclusionType.Full,
                                        FoldableHingeBounds(708.dp, 0.dp, 732.dp, 1200.dp, 24.dp, 1200.dp),
                                        true,
                                    ),
                            )
                        } else {
                            FoldableLayoutInfo()
                        },
                    ) {
                        LogDateTheme(dynamicColor = false) {
                            MemoriesSettingsContent(
                                onBack = {},
                                onNavigateToRecommendations = {},
                                contextualRecommendationsEnabled = false,
                                onToggleContextualRecommendations = {},
                                recallMode = RecallMode.ON_THIS_DAY,
                                onSetRecallMode = {},
                                widgetInstallUiState = MemoriesWidgetInstallUiState.Unsupported,
                                onAddWidgetToHomeScreen = {},
                                widgetContentTypes = WidgetContentType.ALL,
                                onToggleContentType = { _, _ -> },
                                captureModeState = DefaultCaptureModeState.Ready(EntryStartMode.CAMERA),
                            )
                        }
                    }
                }
                assertEquals(1, onAllNodesWithText("Default capture mode").fetchSemanticsNodes().size)
                onNodeWithText("Camera").assertIsSelected()
                saveSettingsPreview(onRoot(), if (book) "book" else "phone")
            }
        }
    }

    @Test
    fun normalSettingsChoicesShowOneSavedSelectionAndSelectTheirActualMode() =
        runDesktopComposeUiTest(width = 411, height = 1000) {
            val selected = mutableStateOf(EntryStartMode.CAMERA)
            setContent {
                LogDateTheme(dynamicColor = false) {
                    DefaultCaptureModeSettingsSection(DefaultCaptureModeState.Ready(selected.value), { selected.value = it })
                }
            }
            for ((label, mode) in listOf(
                "Text" to EntryStartMode.TEXT,
                "Handwriting" to EntryStartMode.HANDWRITING,
                "Camera" to EntryStartMode.CAMERA,
                "Audio" to EntryStartMode.AUDIO,
                "Gallery" to EntryStartMode.GALLERY,
            )) {
                assertEquals(1, onAllNodesWithText(label).fetchSemanticsNodes().size, "Only one preference row for $label")
                onNodeWithText(label).assertIsDisplayed().performClick().assertIsSelected()
                assertEquals(mode, selected.value)
            }
        }

    @Test
    fun failedSaveIsVisibleInLargeTextRtlAndSavingDisablesChoices() =
        runDesktopComposeUiTest(width = 411, height = 1600) {
            val state = mutableStateOf(DefaultCaptureModeState.Ready(EntryStartMode.TEXT, saveFailed = true))
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false, darkTheme = true) {
                        Column { DefaultCaptureModeSettingsSection(state.value, {}) }
                    }
                }
            }
            onNodeWithText("Couldn’t save your default capture mode. Try again.").assertIsDisplayed()
            onNodeWithText("Text").assertIsSelected()
            saveSettingsPreview(onRoot(), "rtl-large-text-failure")
            runOnIdle { state.value = state.value.copy(saving = true) }
            onNodeWithText("Camera").assertIsNotEnabled()
        }
}

private fun saveSettingsPreview(
    root: SemanticsNodeInteraction,
    name: String,
) {
    val output = File(System.getenv("DISK_JANITOR_ARTIFACT_ROOT") ?: "build/reports", "settings-capture-mode").apply { mkdirs() }
    root.captureToImage().asSkiaBitmap().use { bitmap ->
        Image.makeFromBitmap(bitmap).use { image ->
            checkNotNull(image.encodeToData()).use { data -> File(output, "$name.png").writeBytes(data.bytes) }
        }
    }
}
