package app.logdate.feature.postcards

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prints a postcard image via the system print dialog.
 *
 * Takes a content URI pointing to a rendered PNG (from [AndroidExportEngine])
 * and opens the Android print preview. The bitmap is decoded off the main
 * thread — for a large postcard the decode can take a noticeable beat — and
 * the print dialog is handed the bitmap from the main thread so the framework
 * is happy.
 */
suspend fun printPostcard(
    context: Context,
    imageUri: Uri,
    jobName: String = "Postcard",
) {
    val bitmap =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Napier.e("Failed to decode postcard for printing", e)
                null
            }
        }
    if (bitmap == null) {
        Napier.w("Could not decode image for printing: $imageUri")
        return
    }

    withContext(Dispatchers.Main) {
        try {
            @Suppress("DEPRECATION")
            val printHelper = androidx.print.PrintHelper(context as android.app.Activity)
            printHelper.scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
            printHelper.printBitmap(jobName, bitmap)
        } catch (e: Exception) {
            Napier.e("Failed to print postcard", e)
        }
    }
}
