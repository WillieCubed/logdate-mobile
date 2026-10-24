package app.logdate.client.media

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.media.storage.AndroidCanonicalMediaStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Verifies media publication with the Android app sandbox and filesystem policy in effect. */
@RunWith(AndroidJUnit4::class)
class AndroidCanonicalMediaStorePlatformTest {
    @Test
    fun firstImportPublishesAndIdenticalRetryReusesTheObject(): Unit =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val root = File(context.filesDir, "canonical-media-platform-${Uuid.random()}")
            try {
                val bytes = "Android private media publication".encodeToByteArray()
                val store = AndroidCanonicalMediaStore(root)

                val uri = store.store(bytes.inputStream(), "image/png", bytes.size.toLong())
                val retry = AndroidCanonicalMediaStore(root).store(bytes.inputStream(), "image/png", bytes.size.toLong())

                assertEquals(uri, retry)
                assertContentEquals(bytes, File(URI(uri)).readBytes())
            } finally {
                root.deleteRecursively()
            }
        }
}
