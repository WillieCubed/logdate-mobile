package app.logdate.client.media

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class AndroidPhotoLibraryConditionalSaveTest {
    private val payload = MediaPayload("original.png", "image/png", 3, byteArrayOf(1, 2, 3))

    @Test
    fun `photo library decoration preserves guarded original recovery`() =
        runTest {
            val delegate = InMemoryMediaManager()
            val exposed: MediaManager = AndroidPhotoLibraryMediaManager(delegate, mockk<Context>(relaxed = true))
            val saver = assertIs<ConditionalMediaSaver>(exposed)

            val saved = saver.saveMedia(payload) { true }

            assertContentEquals(payload.data, delegate.readMedia(saved).data)
            assertContentEquals(payload.data, exposed.readMedia(saved).data)
        }

    @Test
    fun `account change at the delegate cannot become an unconditional save`() =
        runTest {
            var currentOwner = "original"
            var unconditionalWrites = 0
            val guard = { currentOwner == "original" }
            val delegate =
                object : InMemoryMediaManager() {
                    override suspend fun saveMedia(payload: MediaPayload): String {
                        unconditionalWrites++
                        return super.saveMedia(payload)
                    }

                    override suspend fun saveMedia(
                        payload: MediaPayload,
                        canCommit: () -> Boolean,
                    ): String {
                        assertSame(guard, canCommit)
                        currentOwner = "replacement"
                        return super.saveMedia(payload, canCommit)
                    }
                }
            val exposed: MediaManager = AndroidPhotoLibraryMediaManager(delegate, mockk<Context>(relaxed = true))
            val saver = assertIs<ConditionalMediaSaver>(exposed)

            assertFailsWith<CancellationException> { saver.saveMedia(payload, guard) }
            assertEquals(0, unconditionalWrites)
        }
}
