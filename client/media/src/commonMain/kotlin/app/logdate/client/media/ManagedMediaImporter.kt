package app.logdate.client.media

import io.github.aakira.napier.Napier
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A temporary, private copy of externally-owned media. */
data class StagedMediaFile(
    val sourceFilePath: String,
    val fileName: String,
    val mimeType: String,
)

/** Stages externally-owned media so [MediaManager] can copy it into managed storage. */
interface ManagedMediaImportSource {
    suspend fun stage(sourceUri: String): StagedMediaFile

    suspend fun discard(stagedMedia: StagedMediaFile)
}

/**
 * Copies externally-owned media through [MediaManager] so the returned reference no longer
 * depends on the source URI grant. The platform manager still defines final deletion semantics.
 */
class ManagedMediaImporter(
    private val mediaManager: MediaManager,
    private val source: ManagedMediaImportSource,
    private val discardManagedMedia: suspend (String) -> Unit = {},
) {
    suspend fun import(sourceUri: String): String {
        require(sourceUri.isNotBlank()) { "Source URI cannot be blank" }
        val stagedMedia = source.stage(sourceUri)
        var ownedManagedUri: String? = null

        return try {
            val managedUri =
                mediaManager.saveMediaFromFile(
                    sourceFilePath = stagedMedia.sourceFilePath,
                    fileName = stagedMedia.fileName,
                    mimeType = stagedMedia.mimeType,
                )
            check(managedUri.isNotBlank()) { "Managed media URI cannot be blank" }
            check(managedUri != sourceUri) { "Managed media import returned the source URI" }
            ownedManagedUri = managedUri
            currentCoroutineContext().ensureActive()
            managedUri
        } catch (primaryFailure: Throwable) {
            ownedManagedUri?.let { managedUri ->
                try {
                    withContext(NonCancellable) {
                        discardManagedMedia(managedUri)
                    }
                } catch (cleanupFailure: Throwable) {
                    primaryFailure.addSuppressed(cleanupFailure)
                    Napier.e("Failed to discard an unpublished managed media import", cleanupFailure)
                }
            }
            throw primaryFailure
        } finally {
            withContext(NonCancellable) {
                runCatching { source.discard(stagedMedia) }
                    .onFailure { error -> Napier.w("Failed to remove staged media import", error) }
            }
        }
    }
}

internal fun safeImportedFileName(
    sourceName: String?,
    preferredExtension: String?,
): String {
    val safeLeaf =
        sourceName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.filter { character ->
                !character.isISOControl() &&
                    (character.isLetterOrDigit() || character in SAFE_FILE_NAME_CHARACTERS)
            }?.trim()
            ?.trim('.')
            ?.take(MAX_IMPORTED_FILE_NAME_LENGTH)
            .orEmpty()
            .ifBlank { DEFAULT_IMPORTED_FILE_NAME }
    val safeExtension =
        preferredExtension
            ?.filter(Char::isLetterOrDigit)
            ?.lowercase()
            .orEmpty()

    if (safeExtension.isBlank()) {
        return DEFAULT_IMPORTED_FILE_NAME
    }

    val currentExtension = safeLeaf.substringAfterLast('.', missingDelimiterValue = "")
    if (currentExtension.equals(safeExtension, ignoreCase = true)) {
        return safeLeaf
    }

    val stem =
        safeLeaf
            .substringBeforeLast('.', missingDelimiterValue = safeLeaf)
            .trimEnd('.', ' ')
            .ifBlank { DEFAULT_IMPORTED_FILE_NAME }
            .take(MAX_IMPORTED_FILE_NAME_LENGTH - safeExtension.length - 1)
    return "$stem.$safeExtension"
}

private const val DEFAULT_IMPORTED_FILE_NAME = "imported_media"
private const val MAX_IMPORTED_FILE_NAME_LENGTH = 120
private const val SAFE_FILE_NAME_CHARACTERS = " ._()-"
