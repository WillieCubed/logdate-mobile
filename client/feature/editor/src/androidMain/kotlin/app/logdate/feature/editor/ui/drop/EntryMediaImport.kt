package app.logdate.feature.editor.ui.drop

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import app.logdate.client.media.AndroidManagedMediaDiscarder
import app.logdate.client.media.AndroidManagedMediaImportSource
import app.logdate.client.media.ManagedMediaImporter
import app.logdate.client.media.ManagedMediaPublicationCustody
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** The items in this clip, each with the MIME type its file or description gives it. */
internal fun ClipData.droppedItems(context: Context): List<DroppedItem> =
    (0 until itemCount).map { index ->
        val item = getItemAt(index)
        val mimeType =
            item.uri?.let { context.contentResolver.getType(it) }
                ?: description?.getMimeType(index.coerceAtMost(description.mimeTypeCount - 1))
                ?: ClipDescription.MIMETYPE_TEXT_PLAIN
        DroppedItem(mimeType = mimeType, uri = item.uri?.toString(), text = item.text?.toString())
    }

/**
 * Copies each of [uris], files another app shared by drag and drop, the clipboard, or the keyboard,
 * into LogDate's private media store. A file that cannot be copied is left out, logged, and reported
 * as a [MediaImportProblem]; the others are still imported.
 */
internal suspend fun importIntoEntry(
    context: Context,
    mediaManager: MediaManager,
    publicationCustody: ManagedMediaPublicationCustody,
    uris: List<String>,
): EntryMediaImport {
    val importer =
        ManagedMediaImporter(
            mediaManager = mediaManager,
            source = AndroidManagedMediaImportSource(context),
            discardManagedMedia = AndroidManagedMediaDiscarder(context, mediaManager)::discard,
            publicationCustody = publicationCustody,
        )
    val imported = mutableListOf<String>()
    val problems = mutableListOf<MediaImportProblem>()
    uris.forEach { uri ->
        try {
            imported += importer.import(uri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unsupported: IllegalArgumentException) {
            Napier.w("The file $uri is not a type an entry can hold", unsupported)
            problems += MediaImportProblem.Unsupported
        } catch (error: Exception) {
            Napier.w("Could not add the file $uri to the entry", error)
            problems += MediaImportProblem.Unreadable
        }
    }
    return EntryMediaImport(imported, problems)
}
