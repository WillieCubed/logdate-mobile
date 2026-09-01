package app.logdate.client.media

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun `pre existing media store row is not a deletion capability`() =
        runTest {
            val preExistingUri = Uri.parse("content://media/external/images/media/42")
            val contentResolver = mockk<android.content.ContentResolver>()
            val context = mockk<Context>()
            var deleteCalls = 0
            every { context.applicationContext } returns context
            every { context.contentResolver } returns contentResolver
            every { contentResolver.delete(preExistingUri, null, null) } answers {
                deleteCalls += 1
                1
            }
            val discarder = AndroidManagedMediaDiscarder(context, Dispatchers.Unconfined)

            val failure = runCatching { discarder.discard(preExistingUri.toString()) }.exceptionOrNull()

            assertEquals(
                expected = 0,
                actual = deleteCalls,
                message = "A pre-existing MediaStore URI must never authorize deletion",
            )
            assertIs<IllegalArgumentException>(failure)
        }
}
