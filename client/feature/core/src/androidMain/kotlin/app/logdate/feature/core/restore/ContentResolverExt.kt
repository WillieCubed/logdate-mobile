package app.logdate.feature.core.restore

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Resolves the user-visible display name for a content URI, or null if unavailable.
 */
internal fun ContentResolver.resolveDisplayName(uri: Uri): String? {
    val cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return it.getString(index)
            }
        }
    }
    return null
}
