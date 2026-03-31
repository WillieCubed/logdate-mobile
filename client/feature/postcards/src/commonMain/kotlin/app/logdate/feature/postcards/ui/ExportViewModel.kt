package app.logdate.feature.postcards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.feature.postcards.model.PostcardDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the postcard export flow.
 */
sealed interface ExportUiState {
    data object Idle : ExportUiState

    data class Ready(
        val preset: ExportPreset = ExportPreset.STORY,
    ) : ExportUiState

    data object Rendering : ExportUiState

    data class Complete(
        val result: ExportResult,
    ) : ExportUiState

    data class Failed(
        val message: String,
    ) : ExportUiState
}

/**
 * Manages the postcard export workflow: preset selection, rendering, and sharing.
 */
class ExportViewModel(
    private val exportEngine: ExportEngine,
) : ViewModel() {
    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    /**
     * Opens the export sheet with the default preset.
     */
    fun startExport() {
        _state.value = ExportUiState.Ready()
    }

    /**
     * Selects an export preset (aspect ratio).
     */
    fun selectPreset(preset: ExportPreset) {
        val current = _state.value
        if (current is ExportUiState.Ready) {
            _state.value = current.copy(preset = preset)
        }
    }

    /**
     * Renders the document to PNG and provides the result for sharing.
     */
    fun render(
        document: PostcardDocument,
        stickerUriMap: Map<kotlin.uuid.Uuid, String> = emptyMap(),
    ) {
        val preset = (_state.value as? ExportUiState.Ready)?.preset ?: ExportPreset.STORY
        _state.value = ExportUiState.Rendering

        viewModelScope.launch {
            val captureRegion = computeCaptureRegion(document, preset)
            val result =
                exportEngine.exportToPng(
                    document = document,
                    captureRegion = captureRegion,
                    preset = preset,
                    stickerUriMap = stickerUriMap,
                )
            _state.value =
                if (result != null) {
                    ExportUiState.Complete(result)
                } else {
                    ExportUiState.Failed("Export failed")
                }
        }
    }

    /**
     * Resets back to idle state.
     */
    fun dismiss() {
        _state.value = ExportUiState.Idle
    }
}

/**
 * Computes a capture region that bounds all elements in the document,
 * adjusted to the selected preset's aspect ratio.
 */
internal fun computeCaptureRegion(
    document: PostcardDocument,
    preset: ExportPreset,
): ExportCaptureRegion {
    if (document.elements.isEmpty()) {
        val ratio = preset.widthRatio.toFloat() / preset.heightRatio.toFloat()
        val height = 400f
        val width = height * ratio
        return ExportCaptureRegion(
            x = -width / 2,
            y = -height / 2,
            width = width,
            height = height,
        )
    }

    // Find bounding box of all elements
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    for (element in document.elements) {
        val (w, h) = elementSizeDp(element)
        val x = element.transform.x
        val y = element.transform.y
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x + w)
        maxY = maxOf(maxY, y + h)
    }

    // Expand to match the preset aspect ratio
    val contentWidth = maxX - minX
    val contentHeight = maxY - minY
    val targetRatio = preset.widthRatio.toFloat() / preset.heightRatio.toFloat()
    val contentRatio = contentWidth / contentHeight

    val (finalWidth, finalHeight) =
        if (contentRatio > targetRatio) {
            contentWidth to contentWidth / targetRatio
        } else {
            contentHeight * targetRatio to contentHeight
        }

    // Center the capture region on the content
    val centerX = (minX + maxX) / 2
    val centerY = (minY + maxY) / 2

    return ExportCaptureRegion(
        x = centerX - finalWidth / 2,
        y = centerY - finalHeight / 2,
        width = finalWidth,
        height = finalHeight,
    )
}
