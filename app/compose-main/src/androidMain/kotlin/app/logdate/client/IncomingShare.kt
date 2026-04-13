package app.logdate.client

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutDescriptor
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.uuid.Uuid

internal data class IncomingEditorShare(
    val initialText: String?,
    val attachments: List<String>,
    val targetJournalIds: List<Uuid> = emptyList(),
)

internal suspend fun Context.importIncomingEditorShare(
    intent: Intent,
    mediaManager: MediaManager,
): IncomingEditorShare? =
    withContext(Dispatchers.IO) {
        val action = intent.action ?: return@withContext null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return@withContext null
        }

        val sharedText =
            intent
                .getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotBlank)
        val attachments =
            intent.extractSharedUris().mapNotNull { uri ->
                runCatching { importSharedMedia(uri, mediaManager) }
                    .onFailure { Napier.e("Failed to import shared media: $uri", it) }
                    .getOrNull()
            }
        val targetJournalIds = listOfNotNull(intent.parseShareToJournalShortcutId())

        if (sharedText.isNullOrBlank() && attachments.isEmpty()) {
            null
        } else {
            IncomingEditorShare(sharedText, attachments, targetJournalIds)
        }
    }

/**
 * When the user picks a sharing shortcut from the system share sheet, the OS
 * delivers the original SEND intent with [Intent.EXTRA_SHORTCUT_ID] set to the
 * id of the chosen shortcut. For LogDate's per-journal Direct Share targets,
 * that id encodes the journal UUID via [DynamicShortcutDescriptor.ShareToJournal.ID_PREFIX].
 *
 * Returns the parsed journal UUID, or null when:
 * - No shortcut id was provided (the user shared via the generic LogDate target).
 * - The shortcut id belongs to a launcher shortcut (Continue draft / Today / Rewind),
 *   not a sharing shortcut.
 * - The id format is unrecognised (defensive against future changes).
 */
private fun Intent.parseShareToJournalShortcutId(): Uuid? {
    val shortcutId = getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: return null
    val prefix = "${DynamicShortcutDescriptor.ShareToJournal.ID_PREFIX}:"
    if (!shortcutId.startsWith(prefix)) return null
    val rawUuid = shortcutId.removePrefix(prefix)
    return runCatching { Uuid.parse(rawUuid) }
        .onFailure { Napier.w("Could not parse journal id from sharing shortcut: $shortcutId", it) }
        .getOrNull()
}

private fun Intent.extractSharedUris(): List<Uri> {
    val streamUris =
        when (action) {
            Intent.ACTION_SEND -> listOfNotNull(getParcelableUriExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> getParcelableUriArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }

    if (streamUris.isNotEmpty()) {
        return streamUris.distinct()
    }

    return buildList {
        val clipData = clipData ?: return@buildList
        repeat(clipData.itemCount) { index ->
            clipData.getItemAt(index).uri?.let(::add)
        }
    }.distinct()
}

private fun Intent.getParcelableUriExtra(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }

private fun Intent.getParcelableUriArrayListExtra(name: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }

private suspend fun Context.importSharedMedia(
    uri: Uri,
    mediaManager: MediaManager,
): String? {
    val mimeType = resolveSupportedMimeType(uri) ?: return null
    val fileName = queryDisplayName(uri).orEmpty().ifBlank { fallbackFileName(mimeType) }
    val tempFile = File.createTempFile("logdate_share_", "_$fileName", cacheDir)

    return try {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: return null

        mediaManager.saveMediaFromFile(
            sourceFilePath = tempFile.absolutePath,
            fileName = fileName,
            mimeType = mimeType,
        )
    } finally {
        tempFile.delete()
    }
}

private fun Context.resolveSupportedMimeType(uri: Uri): String? {
    val mimeType =
        contentResolver.getType(uri)
            ?: MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()))
    return mimeType?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
}

private fun Context.queryDisplayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.useCursorValue {
        getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
    }

private inline fun <T> Cursor.useCursorValue(block: Cursor.() -> T): T? {
    if (!moveToFirst()) return null
    return block()
}

private fun fallbackFileName(mimeType: String): String {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty()
    val suffix = if (extension.isBlank()) "" else ".$extension"
    return "shared_media$suffix"
}
