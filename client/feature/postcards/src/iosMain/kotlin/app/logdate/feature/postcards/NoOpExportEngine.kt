package app.logdate.feature.postcards

import app.logdate.feature.postcards.model.PostcardDocument
import app.logdate.feature.postcards.ui.ExportCaptureRegion
import app.logdate.feature.postcards.ui.ExportEngine
import app.logdate.feature.postcards.ui.ExportPreset
import app.logdate.feature.postcards.ui.ExportResult

/**
 * No-op export engine for platforms where export is not yet implemented.
 */
class NoOpExportEngine : ExportEngine {
    override suspend fun exportToPng(
        document: PostcardDocument,
        captureRegion: ExportCaptureRegion,
        preset: ExportPreset,
        targetWidthPx: Int,
    ): ExportResult? = null
}
