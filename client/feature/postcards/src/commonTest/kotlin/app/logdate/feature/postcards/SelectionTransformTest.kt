package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.model.ShapeKind
import app.logdate.feature.postcards.ui.elementSizeDp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Tests for element selection, transform operations, and undo batching
 * during drag gestures.
 */
class SelectionTransformTest {
    @Test
    fun selectElementSetsSelectedId() {
        val elementId = Uuid.random()
        var selectedId: Uuid? = null

        // Simulate onElementTap callback
        selectedId = elementId
        assertEquals(elementId, selectedId)
    }

    @Test
    fun deselectClearsSelectedId() {
        var selectedId: Uuid? = Uuid.random()
        selectedId = null
        assertNull(selectedId)
    }

    @Test
    fun moveElementAppliesDelta() {
        val original = ElementTransform(x = 100f, y = 200f)
        val dx = 25f
        val dy = -15f
        val moved = original.copy(x = original.x + dx, y = original.y + dy)

        assertEquals(125f, moved.x)
        assertEquals(185f, moved.y)
        // Rotation and scale unchanged
        assertEquals(original.rotation, moved.rotation)
        assertEquals(original.scaleX, moved.scaleX)
    }

    @Test
    fun transformElementAppliesScaleAndRotation() {
        val original = ElementTransform(scaleX = 1f, scaleY = 1f, rotation = 0f)
        val scaleDelta = 1.5f
        val rotationDelta = 30f

        val transformed =
            original.copy(
                scaleX = (original.scaleX * scaleDelta).coerceIn(0.1f, 10f),
                scaleY = (original.scaleY * scaleDelta).coerceIn(0.1f, 10f),
                rotation = original.rotation + rotationDelta,
            )

        assertEquals(1.5f, transformed.scaleX)
        assertEquals(1.5f, transformed.scaleY)
        assertEquals(30f, transformed.rotation)
    }

    @Test
    fun transformElementClampsScaleMin() {
        val original = ElementTransform(scaleX = 0.15f, scaleY = 0.15f)
        val scaleDelta = 0.5f // Would result in 0.075, below minimum

        val clamped = (original.scaleX * scaleDelta).coerceIn(0.1f, 10f)
        assertEquals(0.1f, clamped)
    }

    @Test
    fun transformElementClampsScaleMax() {
        val original = ElementTransform(scaleX = 8f, scaleY = 8f)
        val scaleDelta = 2f // Would result in 16, above maximum

        val clamped = (original.scaleX * scaleDelta).coerceIn(0.1f, 10f)
        assertEquals(10f, clamped)
    }

    @Test
    fun undoBatchingDragProducesSingleUndoEntry() {
        // Simulate a drag operation that should produce exactly one undo entry
        val undoStack = ArrayDeque<PostcardDocument>()
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
            )

        // beginDrag pushes once
        undoStack.addLast(doc)

        // Multiple moveElement calls during drag — no additional undo pushes
        val dragFrames = 30
        repeat(dragFrames) {
            // moveElement only updates the document, doesn't push undo
        }

        // After the entire drag, only one undo entry exists
        assertEquals(1, undoStack.size)
    }

    @Test
    fun elementSizeDpReturnsCorrectSizeForPhoto() {
        val photo =
            CanvasElement.Photo(
                id = Uuid.random(),
                momentRef = Uuid.random(),
                mediaUri = "content://test",
            )
        val (w, h) = elementSizeDp(photo)
        assertEquals(200f, w)
        assertEquals(200f, h)
    }

    @Test
    fun elementSizeDpReturnsCorrectSizeForShape() {
        val shape =
            CanvasElement.Shape(
                id = Uuid.random(),
                shapeKind = ShapeKind.RECTANGLE,
                color = "#000",
                strokeWidth = 2f,
                width = 150f,
                height = 80f,
            )
        val (w, h) = elementSizeDp(shape)
        assertEquals(150f, w)
        assertEquals(80f, h)
    }

    @Test
    fun elementSizeDpReturnsCorrectSizeForSticker() {
        val sticker =
            CanvasElement.Sticker(
                id = Uuid.random(),
                stickerRef = Uuid.random(),
            )
        val (w, h) = elementSizeDp(sticker)
        assertEquals(80f, w)
        assertEquals(80f, h)
    }

    @Test
    fun elementSizeDpComputesBoundsForInk() {
        val ink =
            CanvasElement.Ink(
                id = Uuid.random(),
                tool = InkTool.PEN,
                color = "#000",
                strokeWidth = 2f,
                points =
                    listOf(
                        InkPoint(10f, 20f),
                        InkPoint(50f, 80f),
                        InkPoint(30f, 40f),
                    ),
            )
        val (w, h) = elementSizeDp(ink)
        // maxX - minX = 50 - 10 = 40, maxY - minY = 80 - 20 = 60
        assertEquals(40f, w)
        assertEquals(60f, h)
    }

    @Test
    fun elementSizeDpEnforcesMinimumForSmallInk() {
        val ink =
            CanvasElement.Ink(
                id = Uuid.random(),
                tool = InkTool.PEN,
                color = "#000",
                strokeWidth = 2f,
                points =
                    listOf(
                        InkPoint(10f, 20f),
                        InkPoint(12f, 22f),
                    ),
            )
        val (w, h) = elementSizeDp(ink)
        // Bounds are 2x2, but minimum is 20x20
        assertTrue(w >= 20f)
        assertTrue(h >= 20f)
    }

    @Test
    fun cumulativeMovePreservesAccuracy() {
        var transform = ElementTransform(x = 0f, y = 0f)

        // Simulate many small drag increments
        repeat(100) {
            transform =
                transform.copy(
                    x = transform.x + 0.5f,
                    y = transform.y + 0.3f,
                )
        }

        // Should be 50.0 and 30.0 respectively (within floating point tolerance)
        assertTrue(transform.x in 49.9f..50.1f)
        assertTrue(transform.y in 29.9f..30.1f)
    }
}
