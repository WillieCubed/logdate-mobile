package app.logdate.client.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Snapshot of a single row read from a MediaStore Cursor, with every metadata
 * field kept nullable so the fallback logic in [toImage]/[toVideo] can recover
 * gracefully when MediaStore leaves a column blank.
 *
 * Holding the URI as a [String] (rather than [android.net.Uri]) lets the
 * fallback logic be unit-tested on the JVM without booting an emulator or
 * shadowing Android framework types.
 */
internal data class MediaCursorRow(
    val uri: String,
    val displayName: String?,
    val sizeBytes: Int?,
    val durationMillis: Long?,
    val dateTakenMillis: Long?,
    val dateAddedSeconds: Long?,
)

/**
 * Side-effecting recovery operations the fallback chain may need when MediaStore
 * leaves a column null. Hidden behind an interface so tests can supply a fake
 * without touching real Android framework types.
 */
internal interface MediaRecoveryGateway {
    /**
     * Returns the byte size of the file behind [uri], or null if it cannot be
     * read. Used as a fallback when MediaStore's SIZE column is null.
     */
    fun statFileSize(uri: String): Long?

    /**
     * Returns the duration of the video behind [uri], or null if it cannot be
     * read. Used as a fallback when MediaStore's DURATION column is null.
     */
    fun readVideoDuration(uri: String): Duration?
}

/**
 * Materializes a [MediaObject.Image] from a cursor row, applying the fallback
 * chain so the result is always a complete record even when MediaStore left
 * fields blank. The user should never see an image disappear from the gallery
 * because of incomplete MediaStore metadata.
 */
internal fun MediaCursorRow.toImage(
    gateway: MediaRecoveryGateway,
    nowProvider: () -> Instant = { Clock.System.now() },
): MediaObject.Image =
    MediaObject.Image(
        uri = uri,
        name = resolveName(),
        size = resolveSize(gateway),
        timestamp = resolveTimestamp("image", nowProvider),
    )

/**
 * Materializes a [MediaObject.Video] from a cursor row, applying the fallback
 * chain so the result is always a complete record even when MediaStore left
 * the duration or other fields blank. The user should never see a video
 * disappear from the gallery because of incomplete MediaStore metadata.
 */
internal fun MediaCursorRow.toVideo(
    gateway: MediaRecoveryGateway,
    nowProvider: () -> Instant = { Clock.System.now() },
): MediaObject.Video =
    MediaObject.Video(
        uri = uri,
        name = resolveName(),
        size = resolveSize(gateway),
        duration = resolveDuration(gateway),
        timestamp = resolveTimestamp("video", nowProvider),
    )

/**
 * Resolves a display name. Falls back to the URI's last path segment and
 * finally to a generic placeholder so the row never disappears just because
 * its DISPLAY_NAME column was null or blank.
 */
private fun MediaCursorRow.resolveName(): String {
    val cursorName = displayName?.takeIf { it.isNotBlank() }
    if (cursorName != null) return cursorName

    Napier.w("Missing display name for URI: $uri — falling back to URI segment")
    return uri.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "Untitled"
}

/**
 * Resolves a size in bytes. Falls back to a stat against the gateway and
 * finally to 0. Size is informational in the picker so a zero fallback is
 * harmless and keeps the row visible.
 */
private fun MediaCursorRow.resolveSize(gateway: MediaRecoveryGateway): Int {
    if (sizeBytes != null) return sizeBytes

    Napier.w("Missing size metadata for URI: $uri — falling back to file descriptor stat")
    return gateway.statFileSize(uri)?.toInt() ?: 0
}

/**
 * Resolves a video duration. Falls back to reading the underlying file
 * directly via the gateway and finally to [Duration.ZERO], so the user never
 * sees a video disappear just because its cached duration metadata is missing
 * or stale.
 */
private fun MediaCursorRow.resolveDuration(gateway: MediaRecoveryGateway): Duration {
    if (durationMillis != null) return durationMillis.milliseconds

    Napier.w("Missing duration metadata for URI: $uri — reading directly from the file")
    val recovered = gateway.readVideoDuration(uri)
    if (recovered != null) return recovered

    Napier.w("Unable to recover duration for URI: $uri — defaulting to zero so the video stays visible")
    return Duration.ZERO
}

/**
 * Resolves a timestamp, preferring DATE_TAKEN (already in milliseconds) over
 * DATE_ADDED (in seconds, upcasted to millis). Falls back to the current time
 * so the row never disappears just because both columns were missing. This
 * loses precise chronology for the affected row but preserves the data.
 */
private fun MediaCursorRow.resolveTimestamp(
    mediaLabel: String,
    nowProvider: () -> Instant,
): Instant {
    val taken = dateTakenMillis
    if (taken != null && taken > 0L) {
        return Instant.fromEpochMilliseconds(taken)
    }
    val added = dateAddedSeconds
    if (added != null && added > 0L) {
        return Instant.fromEpochMilliseconds(added * 1000)
    }

    Napier.w("Missing $mediaLabel timestamp metadata for URI: $uri — defaulting to now to keep the row visible")
    return nowProvider()
}

/**
 * Production [MediaRecoveryGateway] backed by [ContentResolver] for file
 * descriptor stats and [MediaMetadataRetriever] for direct duration reads.
 * Both operations open the underlying file, so this is only invoked from the
 * fallback paths above when MediaStore's cached metadata is missing.
 */
internal class ContentResolverMediaRecoveryGateway(
    private val contentResolver: ContentResolver,
    private val context: Context,
) : MediaRecoveryGateway {
    override fun statFileSize(uri: String): Long? {
        val parsed =
            try {
                Uri.parse(uri)
            } catch (error: Exception) {
                Napier.w("Unable to parse URI for stat fallback: $uri", error)
                return null
            }
        return try {
            contentResolver.openFileDescriptor(parsed, "r")?.use { it.statSize }
        } catch (error: Exception) {
            Napier.w("Unable to stat file descriptor for URI: $uri", error)
            null
        }
    }

    override fun readVideoDuration(uri: String): Duration? {
        val parsed =
            try {
                Uri.parse(uri)
            } catch (error: Exception) {
                Napier.w("Unable to parse URI for duration fallback: $uri", error)
                return null
            }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, parsed)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.milliseconds
        } catch (error: RuntimeException) {
            Napier.w("Unable to read video duration directly from URI: $uri", error)
            null
        } finally {
            try {
                retriever.release()
            } catch (error: RuntimeException) {
                Napier.e("Failed to release MediaMetadataRetriever", error)
            }
        }
    }
}
