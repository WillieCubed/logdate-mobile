package app.logdate.screenshots.audit.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.postcards.model.CanvasBackground
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.model.ShapeKind
import app.logdate.feature.postcards.ui.PostcardViewerContent
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.adaptive.HingeAwareOverlay
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val TABLETOP_FOLDABLE = "spec:width=1440dp,height=900dp"

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

/**
 * A deterministic, ordinary Postcard for posture screenshots: a quiet evening at home with a short
 * handwritten note, an underline shape, and a small ink doodle. No photos, so it renders without
 * any media loading and stays byte-stable across runs.
 */
private fun samplePostcardDocument(): PostcardDocument =
    PostcardDocument(
        id = Uuid.parse("00000000-0000-0000-0000-000000000118"),
        title = "Quiet Tuesday",
        createdAt = Instant.parse("2026-06-09T19:30:00Z"),
        modifiedAt = Instant.parse("2026-06-09T19:42:00Z"),
        background = CanvasBackground.SolidColor("#FFF5E6"),
        elements =
            listOf(
                CanvasElement.Text(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
                    content = "Leftovers and an early night.",
                    fontFamily = "caveat",
                    color = "#333333",
                    fontSize = 28f,
                    transform = ElementTransform(x = 80f, y = 120f),
                    zIndex = 1,
                ),
                CanvasElement.Shape(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
                    shapeKind = ShapeKind.LINE,
                    color = "#C97B5A",
                    strokeWidth = 3f,
                    width = 240f,
                    height = 0f,
                    transform = ElementTransform(x = 80f, y = 168f),
                    zIndex = 2,
                ),
                CanvasElement.Ink(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000103"),
                    tool = InkTool.PEN,
                    color = "#4A6FA5",
                    strokeWidth = 4f,
                    points =
                        listOf(
                            InkPoint(x = 120f, y = 280f),
                            InkPoint(x = 160f, y = 250f),
                            InkPoint(x = 200f, y = 300f),
                            InkPoint(x = 240f, y = 260f),
                        ),
                    transform = ElementTransform(),
                    zIndex = 3,
                ),
            ),
    )

@PreviewTest
@Preview(name = "Postcard viewer book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A118_PostcardViewerBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            HingeAwareOverlay {
                PostcardViewerContent(
                    document = samplePostcardDocument(),
                    stickerUriMap = emptyMap(),
                    onPhotoTap = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Postcard viewer tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A119_PostcardViewerTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            HingeAwareOverlay {
                PostcardViewerContent(
                    document = samplePostcardDocument(),
                    stickerUriMap = emptyMap(),
                    onPhotoTap = {},
                )
            }
        }
    }
}
