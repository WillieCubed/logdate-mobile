package app.logdate.feature.postcards.ui

import app.logdate.feature.postcards.model.PostcardDocument

/**
 * Preset export aspect ratios.
 */
enum class ExportPreset(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int,
) {
    STORY("Story (9:16)", 9, 16),
    SQUARE("Square (1:1)", 1, 1),
    PORTRAIT("Portrait (4:5)", 4, 5),
}

/**
 * Defines a rectangular capture region over the canvas for export.
 *
 * All values are in canvas coordinate units.
 */
data class ExportCaptureRegion(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Result of an export operation.
 */
data class ExportResult(
    val uri: String,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Renders a [PostcardDocument] to a bitmap image for export.
 *
 * The export process:
 * 1. Define a capture region over the unbounded canvas
 * 2. Choose a target pixel resolution via an [ExportPreset]
 * 3. Render all elements within the capture region to a bitmap
 * 4. Save as PNG to device storage
 *
 * Platform-specific implementations handle the actual bitmap rendering
 * and file I/O.
 */
interface ExportEngine {
    /**
     * Exports the document to a PNG image.
     *
     * @param document The Postcard to export.
     * @param captureRegion The rectangular region of the canvas to capture.
     * @param preset The target aspect ratio and resolution.
     * @param targetWidthPx The target output width in pixels.
     * @return The result containing the saved file URI and dimensions,
     *         or null if export failed.
     */
    suspend fun exportToPng(
        document: PostcardDocument,
        captureRegion: ExportCaptureRegion,
        preset: ExportPreset,
        targetWidthPx: Int = 1080,
    ): ExportResult?
}
