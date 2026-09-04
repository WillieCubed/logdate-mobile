package app.logdate.client.media

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AndroidManagedMediaDiscarderTest {
    @Test
    fun `owned canonical media file is discarded via the media manager`() =
        runTest {
            val managedUri = "file:///data/user/0/app.logdate.test/files/media/objects/sha256/ab/ab123.png"
            val context = mockk<Context>()
            val mediaManager = mockk<app.logdate.client.media.MediaManager>()
            every { context.applicationContext } returns context
            every { context.packageName } returns "app.logdate.test"
            every { context.contentResolver } returns mockk()
            coEvery { mediaManager.deleteOwnedMedia(managedUri) } returns true
            val discarder = AndroidManagedMediaDiscarder(context, mediaManager, Dispatchers.Unconfined)

            discarder.discard(managedUri)

            coVerify(exactly = 1) { mediaManager.deleteOwnedMedia(managedUri) }
        }

    @Test
    fun `pre existing media store row is not a deletion capability`() =
        runTest {
            val preExistingUri = Uri.parse("content://media/external/images/media/42")
            val contentResolver = mockk<android.content.ContentResolver>()
            val context = mockk<Context>()
            var deleteCalls = 0
            val ownerCursor =
                MatrixCursor(arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)).apply {
                    addRow(arrayOf("com.other.gallery.app"))
                }
            every { context.applicationContext } returns context
            every { context.packageName } returns "app.logdate.test"
            every { context.contentResolver } returns contentResolver
            every {
                contentResolver.query(
                    preExistingUri,
                    arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                    null,
                    null,
                    null,
                )
            } returns ownerCursor
            every { contentResolver.delete(preExistingUri, null, null) } answers {
                deleteCalls += 1
                1
            }
            val mediaManager = mockk<app.logdate.client.media.MediaManager>()
            val discarder = AndroidManagedMediaDiscarder(context, mediaManager, Dispatchers.Unconfined)

            val failure = runCatching { discarder.discard(preExistingUri.toString()) }.exceptionOrNull()

            assertEquals(
                expected = 0,
                actual = deleteCalls,
                message = "A pre-existing MediaStore URI must never authorize deletion",
            )
            assertIs<IllegalArgumentException>(failure)
        }
}
