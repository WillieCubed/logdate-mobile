package app.logdate.client.e2e

import android.content.ContentValues
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.AndroidMediaManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WidgetRecentImagesTest {
    @Test
    fun `recent device photo is exposed without querying videos`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        val hadPermission = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (!hadPermission) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "logdate-widget-photo-test.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        ) ?: error("Could not create emulator photo")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: error("Could not write emulator photo")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val images = AndroidMediaManager(resolver, context).getRecentImages(20).first()
            assertTrue(images.any { it.name == "logdate-widget-photo-test.png" }, "The widget must see a recent MediaStore photo: $images")
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
