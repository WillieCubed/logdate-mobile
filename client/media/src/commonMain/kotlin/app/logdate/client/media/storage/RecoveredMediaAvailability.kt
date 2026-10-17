package app.logdate.client.media.storage

import app.logdate.client.media.MediaManager
import kotlinx.coroutines.CancellationException
import kotlinx.io.Buffer

/** Restored originals need readable private bytes; a provider metadata query proves neither identity nor access. */
suspend fun MediaManager.isRecoveredOriginal(
    reference: String,
    references: StoredMediaReferences,
): Boolean {
    if (MediaReference.parse(references.storedReference(reference)) !is MediaReference.Owned) return false
    return try {
        if (!exists(reference)) return false
        val original = openMedia(reference)
        original.sizeBytes > 0 && original.open().use { it.readAtMostTo(Buffer(), 1) > 0 }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
