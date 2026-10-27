package app.logdate.feature.core.sync

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.conflict.DeletedEdit
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke
import app.logdate.shared.model.SerializableHandwritingBlock
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class HandwritingRecoveryPreviewTest {
    @Test
    fun `an offline handwritten note shows its original ink for recovery`() =
        verifyPreview(DeletedEdit(note = JournalNote.Handwriting(creationTimestamp = at, lastUpdated = at, document = document())))

    @Test
    fun `an offline handwritten draft shows its original ink for recovery`() =
        verifyPreview(
            DeletedEdit(draft = EditorDraft(blocks = listOf(SerializableHandwritingBlock(Uuid.random(), at, document = document())))),
        )

    @Test
    fun `a retained handwritten note remains readable on a dark tablet`() =
        verifyPreview(
            DeletedEdit(note = JournalNote.Handwriting(creationTimestamp = at, lastUpdated = at, document = document())),
            width = 840,
            height = 1000,
            dark = true,
        )

    @Test
    fun `a retained handwritten draft fits RTL and large text`() =
        verifyPreview(
            DeletedEdit(draft = EditorDraft(blocks = listOf(SerializableHandwritingBlock(Uuid.random(), at, document = document())))),
            rtl = true,
            scale = 2f,
        )

    private fun verifyPreview(
        edit: DeletedEdit,
        width: Int = 411,
        height: Int = 891,
        dark: Boolean = false,
        rtl: Boolean = false,
        scale: Float = 1f,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val record =
            SyncConflictRecord(
                "recovery",
                "NOTE",
                "original",
                2,
                3,
                1,
                2,
                "DELETION_CONFLICT",
                3,
                scope = UploadScope("owner", "origin"),
                deletedEdit = edit,
            )
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, scale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                LogDateTheme(darkTheme = dark) { DeletionRecoveryContent(listOf(record), null, false, false, {}, {}, {}) }
            }
        }
        val preview = onNodeWithContentDescription("Original handwriting").performScrollTo()
        val bounds = preview.getBoundsInRoot()
        val root = onRoot()
        val viewport = root.getBoundsInRoot()
        assertTrue(
            (bounds.right - bounds.left).value > 200f && (bounds.bottom - bounds.top).value > 100f,
            "Original ink needs a visible reading surface: $bounds",
        )
        assertTrue(
            bounds.top >= viewport.top && bounds.bottom <= viewport.bottom,
            "Original ink must fit in the viewport: $bounds / $viewport",
        )
        val pixels = root.captureToImage().toPixelMap()
        val scaleX = pixels.width / (viewport.right - viewport.left).value
        val scaleY = pixels.height / (viewport.bottom - viewport.top).value
        val left = ((bounds.left - viewport.left).value * scaleX).toInt().coerceIn(0, pixels.width)
        val right = ((bounds.right - viewport.left).value * scaleX).toInt().coerceIn(left, pixels.width)
        val top = ((bounds.top - viewport.top).value * scaleY).toInt().coerceIn(0, pixels.height)
        val bottom = ((bounds.bottom - viewport.top).value * scaleY).toInt().coerceIn(top, pixels.height)
        var inkPixels = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val color = pixels[x, y]
                if (color.red < 0.4f && color.green < 0.4f && color.blue < 0.5f) inkPixels++
            }
        }
        assertTrue(inkPixels > 50, "The preview must render the strokes, not an empty surface")
        val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(x, y, pixels[x, y].toArgb())
        val output = System.getenv("DISK_JANITOR_ARTIFACT_ROOT")?.let { File(it, "renders") } ?: File("build/reports/sync-renders")
        output.mkdirs()
        val kind = if (edit.note != null) "note" else "draft"
        ImageIO.write(image, "png", File(output, "handwriting-recovery-$kind-$width-dark$dark-rtl$rtl-scale$scale.png"))
    }

    private fun document() =
        HandwritingDocument(
            strokes =
                listOf(
                    HandwritingStroke(
                        Uuid.random(),
                        "pen",
                        "#172634",
                        12f,
                        listOf(HandwritingPoint(50f, 100f, 0.6f), HandwritingPoint(650f, 500f, 0.9f)),
                    ),
                ),
        )

    private val at = Instant.parse("2026-10-27T10:00:00Z")
}
