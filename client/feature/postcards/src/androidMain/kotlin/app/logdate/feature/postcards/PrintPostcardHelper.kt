package app.logdate.feature.postcards

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.aakira.napier.Napier

/**
 * Prints a postcard image via the system print dialog.
 *
 * Takes a content URI pointing to a rendered PNG (from [AndroidExportEngine])
 * and opens the Android print preview.
 */
fun printPostcard(
    context: Context,
    imageUri: Uri,
    jobName: String = "Postcard",
) {
    try {
        val inputStream =
            context.contentResolver.openInputStream(imageUri) ?: run {
                Napier.w("Could not open image for printing: $imageUri")
                return
            }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) {
            Napier.w("Could not decode image for printing: $imageUri")
            return
        }

        @Suppress("DEPRECATION")
        val printHelper = androidx.print.PrintHelper(context as android.app.Activity)
        printHelper.scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
        printHelper.printBitmap(jobName, bitmap)
    } catch (e: Exception) {
        Napier.e("Failed to print postcard", e)
    }
}
