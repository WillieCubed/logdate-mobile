package app.logdate.feature.postcards

import android.content.Context
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies a content URI to a destination URI chosen by the user via the system file picker.
 *
 * Runs the copy on [Dispatchers.IO] to avoid blocking the main thread.
 * Returns true on success, false on failure.
 */
suspend fun copyUriToDestination(
    context: Context,
    sourceUri: Uri,
    destinationUri: Uri,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    input.copyTo(output)
                    return@withContext true
                }
            }
            Napier.w("Could not open streams for file save")
            false
        } catch (e: Exception) {
            Napier.e("Failed to save file to destination", e)
            false
        }
    }
