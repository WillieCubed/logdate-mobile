package app.logdate.client.media

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidManagedMediaDiscarderTest {
    @Test
    fun `an aborted import never deletes a canonical object that another entry may use`() =
        runTest {
            val managedUri = "file:///data/user/0/app.logdate.test/files/media/objects/sha256/ab/ab123.png"
            val context = mockk<Context>()
            val mediaManager = mockk<app.logdate.client.media.MediaManager>()
            every { context.applicationContext } returns context
            every { context.packageName } returns "app.logdate.test"
            every { context.contentResolver } returns mockk()
            val discarder = AndroidManagedMediaDiscarder(context, mediaManager, Dispatchers.Unconfined)

            discarder.discard(managedUri)

            coVerify(exactly = 0) { mediaManager.deleteOwnedMedia(any()) }
        }

    @Test
    fun `a media store URI is not an exclusive deletion capability`() =
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

            discarder.discard(preExistingUri.toString())

            assertEquals(
                expected = 0,
                actual = deleteCalls,
                message = "A pre-existing MediaStore URI must never authorize deletion",
            )
        }

    @Test
    fun `an app owned media store row is retained when an import aborts`() =
        runTest {
            val uri = Uri.parse("content://media/external/images/media/43")
            val resolver = mockk<android.content.ContentResolver>()
            val context = mockk<Context>()
            every { context.applicationContext } returns context
            every { context.packageName } returns "app.logdate.test"
            every { context.contentResolver } returns resolver
            every {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)
            } returns
                MatrixCursor(arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)).apply {
                    addRow(arrayOf("app.logdate.test"))
                }
            every { resolver.delete(uri, null, null) } returns 1

            AndroidManagedMediaDiscarder(context, mockk(), Dispatchers.Unconfined).discard(uri.toString())

            io.mockk.verify(exactly = 0) { resolver.delete(any(), any(), any()) }
        }
}
