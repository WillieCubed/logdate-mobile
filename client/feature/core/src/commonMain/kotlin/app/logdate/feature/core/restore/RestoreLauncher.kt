package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.IntegrityMismatch
import app.logdate.client.domain.restore.RestoreResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.restore_stage_copying
import logdate.client.feature.core.generated.resources.restore_stage_drafts
import logdate.client.feature.core.generated.resources.restore_stage_journals
import logdate.client.feature.core.generated.resources.restore_stage_links
import logdate.client.feature.core.generated.resources.restore_stage_location_history
import logdate.client.feature.core.generated.resources.restore_stage_media
import logdate.client.feature.core.generated.resources.restore_stage_notes
import logdate.client.feature.core.generated.resources.restore_stage_opening
import logdate.client.feature.core.generated.resources.restore_stage_places
import logdate.client.feature.core.generated.resources.restore_stage_preparing
import logdate.client.feature.core.generated.resources.restore_stage_profile
import logdate.client.feature.core.generated.resources.restore_stage_reading
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * Interface for launching data restore operations.
 */
interface RestoreLauncher {
    /**
     * Opens the file picker for archive selection without starting a restore.
     */
    fun startFileSelection()

    /**
     * Starts the data restore process with the given options.
     *
     * Must be called after [startFileSelection] has completed and a file has
     * been selected (delivered via the file-selected callback).
     */
    fun startRestore(options: ImportOptions = ImportOptions())

    /**
     * Cancels any ongoing restore operation.
     */
    fun cancelRestore()

    /**
     * Sets a callback to be notified of restore progress and completion.
     */
    fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit)

    /**
     * Sets a callback to be notified when the user selects (or cancels) a file.
     */
    fun setFileSelectedCallback(callback: (ArchiveFileInfo?) -> Unit)

    /**
     * Updates the restore progress directly. Called by platform-specific workers
     * to bypass rate-limited delivery mechanisms.
     */
    fun updateProgress(info: RestoreProgressInfo)

    /**
     * Signals restore completion directly. Called by platform-specific workers
     * to report final outcome independent of system completion mechanisms.
     */
    fun completeRestore(outcome: RestoreOutcome)

    /**
     * Observable progress stream for the current restore operation.
     */
    val restoreProgress: StateFlow<RestoreProgressInfo>
}

sealed class RestoreOutcome {
    data object Started : RestoreOutcome()

    data object Cancelled : RestoreOutcome()

    data class Success(
        val summary: RestoreSummary,
    ) : RestoreOutcome()

    data class Failure(
        val error: RestoreError,
    ) : RestoreOutcome()
}

/**
 * Typed restore errors. The UI layer resolves these to localized strings.
 */
enum class RestoreError {
    INVALID_ARCHIVE,
    MISSING_SOURCE,
    FILE_NOT_ACCESSIBLE,
    FILE_PICKER_UNAVAILABLE,
    FILE_PICKER_FAILED,
    INVALID_SUMMARY,
    NO_SUMMARY_RETURNED,
    IOS_REQUIRES_FOLDER,
    UNSUPPORTED_VERSION,
    RESTORE_FAILED,
}

/**
 * Options controlling what data to include in the import.
 */
data class ImportOptions(
    val includeDrafts: Boolean = true,
    val includeMedia: Boolean = true,
)

/**
 * Progress information for the current restore operation.
 */
sealed interface RestoreProgressInfo {
    data object Idle : RestoreProgressInfo

    data class Active(
        val stage: RestoreStage,
        val progressPercent: Int,
    ) : RestoreProgressInfo
}

/**
 * Typed progress stages. The UI layer resolves these to localized strings.
 */
enum class RestoreStage {
    IDLE,
    PREPARING,
    COPYING_ARCHIVE,
    OPENING_ARCHIVE,
    READING_CONTENTS,
    RESTORING_JOURNALS,
    RESTORING_NOTES,
    RESTORING_LINKS,
    RESTORING_DRAFTS,
    RESTORING_PROFILE,
    RESTORING_PLACES,
    RESTORING_LOCATION_HISTORY,
    IMPORTING_MEDIA,
}

val RestoreStage.defaultProgressPercent: Int
    get() =
        when (this) {
            RestoreStage.IDLE -> 0
            RestoreStage.PREPARING -> 0
            RestoreStage.COPYING_ARCHIVE -> 5
            RestoreStage.OPENING_ARCHIVE -> 10
            RestoreStage.READING_CONTENTS -> 20
            RestoreStage.RESTORING_JOURNALS -> 40
            RestoreStage.RESTORING_NOTES -> 52
            RestoreStage.RESTORING_LINKS -> 64
            RestoreStage.RESTORING_DRAFTS -> 76
            RestoreStage.RESTORING_PROFILE -> 84
            RestoreStage.RESTORING_PLACES -> 90
            RestoreStage.RESTORING_LOCATION_HISTORY -> 96
            RestoreStage.IMPORTING_MEDIA -> 98
        }

val RestoreStage.labelResource: StringResource?
    get() =
        when (this) {
            RestoreStage.IDLE -> null
            RestoreStage.PREPARING -> Res.string.restore_stage_preparing
            RestoreStage.COPYING_ARCHIVE -> Res.string.restore_stage_copying
            RestoreStage.OPENING_ARCHIVE -> Res.string.restore_stage_opening
            RestoreStage.READING_CONTENTS -> Res.string.restore_stage_reading
            RestoreStage.RESTORING_JOURNALS -> Res.string.restore_stage_journals
            RestoreStage.RESTORING_NOTES -> Res.string.restore_stage_notes
            RestoreStage.RESTORING_LINKS -> Res.string.restore_stage_links
            RestoreStage.RESTORING_DRAFTS -> Res.string.restore_stage_drafts
            RestoreStage.RESTORING_PROFILE -> Res.string.restore_stage_profile
            RestoreStage.RESTORING_PLACES -> Res.string.restore_stage_places
            RestoreStage.RESTORING_LOCATION_HISTORY -> Res.string.restore_stage_location_history
            RestoreStage.IMPORTING_MEDIA -> Res.string.restore_stage_media
        }

/**
 * Metadata extracted from a selected archive file for preview.
 */
data class ArchiveFileInfo(
    val displayName: String,
    val uri: String,
    val metadataJson: String? = null,
    val archiveFormat: RestoreArchiveFormat = RestoreArchiveFormat.LegacyZip,
)

enum class RestoreArchiveFormat {
    EncryptedBackup,
    LegacyZip,
}

@Serializable
data class RestoreSummary(
    val source: String,
    val exportDate: Instant? = null,
    val appVersion: String? = null,
    val deviceId: String? = null,
    val journalsImported: Int,
    val notesImported: Int,
    val draftsImported: Int,
    val journalLinksImported: Int,
    val mediaImported: Int,
    val warnings: List<String> = emptyList(),
    val integrityMismatches: List<IntegrityMismatch> = emptyList(),
)

fun RestoreResult.toSummary(source: String): RestoreSummary =
    RestoreSummary(
        source = source,
        exportDate = metadata.exportDate,
        appVersion = metadata.appVersion,
        deviceId = metadata.deviceId,
        journalsImported = journalsImported,
        notesImported = notesImported,
        draftsImported = draftsImported,
        journalLinksImported = journalLinksImported,
        mediaImported = mediaImported,
        warnings = warnings,
        integrityMismatches = integrityMismatches,
    )
