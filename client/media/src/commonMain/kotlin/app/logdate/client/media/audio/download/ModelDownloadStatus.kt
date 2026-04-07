package app.logdate.client.media.audio.download

/**
 * Status of an on-device speech / audio-tagging model download.
 *
 * Both [app.logdate.client.media.audio.transcription.TranscriptionService.downloadOfflineModel]
 * and [app.logdate.client.media.audio.tagging.AudioTaggingService.downloadModel] emit this
 * sealed type so the UI can render a single download progress component for either, and
 * map each failure case to a localized string at the call site.
 */
sealed interface ModelDownloadStatus {
    /** No download has started yet. */
    data object Idle : ModelDownloadStatus

    /**
     * HTTP body is streaming to disk. [bytesDownloaded] grows over time;
     * [totalBytes] is `null` when the server didn't send Content-Length, in
     * which case the UI should show an indeterminate progress indicator.
     */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : ModelDownloadStatus {
        /** 0..1 progress fraction, or `null` when [totalBytes] is unknown. */
        val fraction: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it.toFloat() }
    }

    /**
     * Bytes have finished downloading; the archive is being unpacked into
     * its final location. Typically lasts a second or two.
     */
    data object Extracting : ModelDownloadStatus

    /** The model is on disk and ready to use. */
    data object Completed : ModelDownloadStatus

    // --- Failure terminal states. The UI maps each to a localized string. ---

    /** No internet, DNS failure, or host unreachable. */
    data object NoNetwork : ModelDownloadStatus

    /** The host returned a non-success status (gone, server error, etc.). */
    data object ServerUnavailable : ModelDownloadStatus

    /** Not enough free storage to fit the extracted model. */
    data object OutOfStorage : ModelDownloadStatus

    /** The downloaded archive failed to unpack — corrupt or unexpected layout. */
    data object ArchiveCorrupt : ModelDownloadStatus

    /** This implementation doesn't support a downloadable model at all. */
    data object NotSupported : ModelDownloadStatus

    /** Anything else. The triggering exception is in the logs. */
    data object UnknownError : ModelDownloadStatus
}
