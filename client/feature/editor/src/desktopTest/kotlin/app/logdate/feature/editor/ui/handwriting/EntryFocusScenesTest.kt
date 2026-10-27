package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
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
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryFocusScenesTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
) {
    @Test
    fun capture() =
        runDesktopComposeUiTest(width = width, height = height) {
            val ink = HandwritingBlockUiState(page = sampleInk())
            val text = TextBlockUiState(content = "Took the long way home.\n\nThe light was too good to miss.")
            val photo = ImageBlockUiState(uri = samplePhotoUri(), caption = "A quiet moment outside")
            val audio = AudioBlockUiState()
            val selected =
                when {
                    name.endsWith("-ink") -> ink.id
                    name.endsWith("-text") -> text.id
                    name.endsWith("-photo") -> photo.id
                    name.endsWith("-recorder") -> audio.id
                    else -> null
                }
            val posture =
                when {
                    name.contains("book") -> FoldablePosture.Book
                    name.contains("tabletop") -> FoldablePosture.Tabletop
                    else -> FoldablePosture.Standard
                }
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, if (name.contains("large-text")) 2f else 1f),
                    LocalLayoutDirection provides if (name.contains("rtl")) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    provideFoldableLayoutInfo(foldFixture(posture, width, height)) {
                        LogDateTheme(darkTheme = name.contains("dark"), dynamicColor = false) {
                            EntryStartPrototype(
                                initialBlocks = listOf(ink, text, photo, audio),
                                initialFocusedBlockId = selected,
                                focusAnyBlock = true,
                                previewPresentations = mapOf(recorderFixture(audio)),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            File("build/reports/entry-focus/$name.png").apply {
                parentFile.mkdirs()
                writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
            }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone-overview", 360, 800),
                arrayOf("phone-ink", 360, 800),
                arrayOf("phone-text", 360, 800),
                arrayOf("phone-photo", 360, 800),
                arrayOf("phone-recorder", 360, 800),
                arrayOf("phone-dark-overview", 360, 800),
                arrayOf("phone-dark-ink", 360, 800),
                arrayOf("compact-overview", 320, 640),
                arrayOf("landscape-text", 800, 360),
                arrayOf("tablet-overview", 1280, 800),
                arrayOf("tablet-ink", 1280, 800),
                arrayOf("tablet-photo", 1280, 800),
                arrayOf("tablet-recorder", 1280, 800),
                arrayOf("medium-overview", 700, 900),
                arrayOf("book-overview", 1000, 800),
                arrayOf("book-text", 1000, 800),
                arrayOf("tabletop-recorder", 1000, 800),
                arrayOf("phone-rtl-overview", 360, 800),
                arrayOf("phone-large-text-overview", 360, 800),
            )
    }
}

internal fun foldFixture(
    posture: FoldablePosture,
    width: Int,
    height: Int,
): FoldableLayoutInfo {
    if (posture == FoldablePosture.Standard) return FoldableLayoutInfo()
    val book = posture == FoldablePosture.Book
    val bounds =
        if (book) {
            FoldableHingeBounds((width / 2 - 12).dp, 0.dp, (width / 2 + 12).dp, height.dp, 24.dp, height.dp)
        } else {
            FoldableHingeBounds(0.dp, (height / 2 - 12).dp, width.dp, (height / 2 + 12).dp, width.dp, 24.dp)
        }
    return FoldableLayoutInfo(
        isFoldable = true,
        posture = posture,
        hinge =
            FoldableHingeInfo(
                orientation = if (book) FoldableHingeOrientation.Vertical else FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds = bounds,
                isSeparating = true,
            ),
    )
}
