package app.logdate.client.e2e

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.feature.widgets.loadScaledThumbnail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WidgetThumbnailTest {
    @Test
    fun `large device photo is bounded before embedding in widget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "widget-large-photo-test.jpg")
        val source = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        var mediaUri: android.net.Uri? = null
        try {
            FileOutputStream(file).use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val thumbnail = assertNotNull(loadScaledThumbnail(context, file.absolutePath))
            assertTrue(thumbnail.width <= 384)
            assertTrue(thumbnail.height <= 384)
            assertTrue(thumbnail.byteCount <= 384 * 384 * 4)

            mediaUri = context.contentResolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "logdate-widget-large-photo-test.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            )
            val uri = assertNotNull(mediaUri)
            context.contentResolver.openOutputStream(uri)?.use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val deviceThumbnail = assertNotNull(loadScaledThumbnail(context, uri.toString()))
            assertTrue(deviceThumbnail.width <= 384)
            assertTrue(deviceThumbnail.height <= 384)
            assertTrue(deviceThumbnail.byteCount <= 384 * 384 * 4)
        } finally {
            source.recycle()
            mediaUri?.let { context.contentResolver.delete(it, null, null) }
            file.delete()
        }
    }
}
