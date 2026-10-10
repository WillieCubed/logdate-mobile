package app.logdate.client.e2e

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.feature.widgets.loadScaledThumbnail
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaPayload
import app.logdate.client.media.storage.androidMediaFileResolver
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.core.export.AndroidMediaSourceOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stored media references open through every Android reader on a real device runtime: widgets,
 * the media manager, sharing and export all accept `logdate-media://` references and the
 * single-slash `file:/` URIs the private media store writes.
 */
@RunWith(AndroidJUnit4::class)
class PortableMediaReferencesE2ETest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaFiles = androidMediaFileResolver(context)
    private val mediaManager = AndroidMediaManager(context.contentResolver, context, Dispatchers.Unconfined)
    private lateinit var photo: File
    private lateinit var jpeg: ByteArray

    @Before
    fun writePhoto() {
        jpeg =
            ByteArrayOutputStream()
                .also { out ->
                    Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
                }.toByteArray()
        photo = File(context.filesDir, "media/portable reference test.jpg").apply { parentFile?.mkdirs() }
        photo.writeBytes(jpeg)
    }

    @After
    fun removePhoto() {
        photo.delete()
    }

    @Test
    fun widgetThumbnailLoadsAReferenceAndASingleSlashFileUri() {
        assertNotNull(loadScaledThumbnail(context, "logdate-media://library/portable%20reference%20test.jpg"))
        assertTrue(photo.toURI().toString().startsWith("file:/"))
        assertNotNull(loadScaledThumbnail(context, photo.toURI().toString()))
    }

    @Test
    fun mediaManagerReadsAReferenceAndASingleSlashFileUri() =
        runBlocking {
            val reference = "logdate-media://library/portable%20reference%20test.jpg"

            assertTrue(mediaManager.exists(reference))
            assertTrue(mediaManager.exists(photo.toURI().toString()))
            assertEquals(jpeg.size, mediaManager.readMedia(reference).data.size)
            assertEquals(jpeg.size.toLong(), mediaManager.openMedia(reference).sizeBytes)
        }

    @Test
    fun mediaManagerDeletesWhatItSavedByReference() =
        runBlocking {
            val saved = mediaManager.saveMedia(MediaPayload("saved.jpg", "image/jpeg", jpeg.size.toLong(), jpeg))
            val reference = mediaFiles.storedReference(saved)
            assertTrue(reference.startsWith("logdate-media://library/objects/sha256/"), reference)
            assertTrue(mediaManager.exists(reference))

            assertTrue(mediaManager.deleteOwnedMedia(reference))

            assertFalse(mediaManager.exists(reference))
        }

    @Test
    fun sharingExposesAReferenceThroughTheFileProvider() {
        val launcher = GlobalContext.get().get<SharingLauncher>()

        val uri = launcher.getUriFromMedia("logdate-media://library/portable%20reference%20test.jpg") as Uri

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.provider", uri.authority)
        assertEquals(jpeg.size, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.size)
    }

    @Test
    fun exportOpensAReference() =
        runBlocking {
            val source = assertNotNull(AndroidMediaSourceOpener(context).open("logdate-media://library/portable%20reference%20test.jpg"))

            assertEquals(jpeg.size, source.buffer().use { it.readByteArray() }.size)
        }
}
