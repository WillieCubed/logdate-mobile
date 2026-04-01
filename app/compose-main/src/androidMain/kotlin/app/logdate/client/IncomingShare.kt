package app.logdate.client

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal data class IncomingEditorShare(
    val initialText: String?,
    val attachments: List<String>,
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

        if (sharedText.isNullOrBlank() && attachments.isEmpty()) {
            null
        } else {
            IncomingEditorShare(sharedText, attachments)
        }
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
