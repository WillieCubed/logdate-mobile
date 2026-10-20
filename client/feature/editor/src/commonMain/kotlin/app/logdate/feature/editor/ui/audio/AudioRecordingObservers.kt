package app.logdate.feature.editor.ui.audio
import app.logdate.client.media.audio.download.ModelDownloadStatus

/**
 * Combines the per-model download statuses into the single
 * [EnhancedAudioModelStatus] the audio editor banner reads. The banner
 * disappears once both models are present, and otherwise reflects the
 * highest-priority state across the two — a failure wins over a download in
 * progress, which wins over an idle missing model.
 */
internal fun deriveEnhancedAudioModelStatus(
    transcription: ModelDownloadStatus,
    tagging: ModelDownloadStatus,
): EnhancedAudioModelStatus {
    val both = listOf(transcription, tagging)

    val failure = both.firstOrNull { it is ModelDownloadStatus.Failure }
    if (failure != null) return EnhancedAudioModelStatus.Failed(failure)

    val downloads = both.filterIsInstance<ModelDownloadStatus.Downloading>()
    if (downloads.isNotEmpty()) {
        // Single pass: bail to indeterminate (null) the moment we see an
        // unknown total; otherwise accumulate the sum and divide once.
        var sum = 0.0
        var hasUnknown = false
        for (download in downloads) {
            val fraction = download.fraction
            if (fraction == null) {
                hasUnknown = true
                break
            }
            sum += fraction
        }
        val combined = if (hasUnknown) null else (sum / downloads.size).toFloat()
        return EnhancedAudioModelStatus.Downloading(combined)
    }

    if (both.any { it == ModelDownloadStatus.Extracting }) {
        return EnhancedAudioModelStatus.Downloading(null)
    }

    if (both.all { it == ModelDownloadStatus.Completed }) {
        return EnhancedAudioModelStatus.Ready
    }

    return EnhancedAudioModelStatus.NotDownloaded
}
