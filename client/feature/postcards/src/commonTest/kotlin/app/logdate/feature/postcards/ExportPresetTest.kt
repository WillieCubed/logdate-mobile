package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.ExportPreset
import app.logdate.feature.postcards.ui.computeCaptureRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Tests for export capture region calculation and aspect ratio math.
 */
class ExportPresetTest {
    @Test
    fun storyPresetHas9to16Ratio() {
        assertEquals(9, ExportPreset.STORY.widthRatio)
        assertEquals(16, ExportPreset.STORY.heightRatio)
    }

    @Test
    fun squarePresetHas1to1Ratio() {
        assertEquals(1, ExportPreset.SQUARE.widthRatio)
        assertEquals(1, ExportPreset.SQUARE.heightRatio)
    }

    @Test
    fun portraitPresetHas4to5Ratio() {
        assertEquals(4, ExportPreset.PORTRAIT.widthRatio)
        assertEquals(5, ExportPreset.PORTRAIT.heightRatio)
    }

    @Test
    fun captureRegionForEmptyDocumentUsesDefaultSize() {
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
            )
        val region = computeCaptureRegion(doc, ExportPreset.SQUARE)

        assertEquals(region.width, region.height)
        assertTrue(region.width > 0f)
    }

    @Test
    fun captureRegionSquareProducesEqualDimensions() {
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
            )
        val region = computeCaptureRegion(doc, ExportPreset.SQUARE)

        val ratio = region.width / region.height
        assertTrue(ratio in 0.99f..1.01f)
    }

    @Test
    fun captureRegionStoryIsTallerThanWide() {
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
                elements =
                    listOf(
                        CanvasElement.Photo(
                            id = Uuid.random(),
                            momentRef = Uuid.random(),
                            mediaUri = "content://test",
                            transform = ElementTransform(x = 0f, y = 0f),
                            zIndex = 0,
                        ),
                    ),
            )
        val region = computeCaptureRegion(doc, ExportPreset.STORY)

        assertTrue(region.height > region.width)
    }

    @Test
    fun captureRegionBoundsAllElements() {
        val doc =
            PostcardDocument(
                id = Uuid.random(),
                title = "Test",
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
                elements =
                    listOf(
                        CanvasElement.Photo(
                            id = Uuid.random(),
                            momentRef = Uuid.random(),
                            mediaUri = "content://test1",
                            transform = ElementTransform(x = -100f, y = -50f),
                            zIndex = 0,
                        ),
                        CanvasElement.Photo(
                            id = Uuid.random(),
                            momentRef = Uuid.random(),
                            mediaUri = "content://test2",
                            transform = ElementTransform(x = 200f, y = 300f),
                            zIndex = 1,
                        ),
                    ),
            )
        val region = computeCaptureRegion(doc, ExportPreset.SQUARE)

        // Region should contain both elements
        assertTrue(region.x <= -100f)
        assertTrue(region.y <= -50f)
        assertTrue(region.x + region.width >= 200f + 200f) // x + photo width
        assertTrue(region.y + region.height >= 300f + 200f) // y + photo height
    }
}
