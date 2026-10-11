package app.logdate.client.sync.v2.receipts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneRequestAnswerTest {
    @Test
    fun `a response completes the request`() =
        runTest {
            val task = answerPhoneRequest(backgroundScope) { byteArrayOf(1, 2) }
            runCurrent()
            assertTrue(task.isComplete)
            assertContentEquals(byteArrayOf(1, 2), task.result)
        }

    @Test
    fun `an exception completes the request as failed`() =
        runTest {
            val task = answerPhoneRequest(backgroundScope) { error("unavailable") }
            runCurrent()
            assertTrue(task.isComplete)
            assertFalse(task.isSuccessful)
        }

    @Test
    fun `an unsupported request completes as failed`() =
        runTest {
            val task = answerPhoneRequest(backgroundScope) { null }
            runCurrent()
            assertTrue(task.isComplete)
            assertFalse(task.isSuccessful)
        }

    @Test
    fun `a cancelled service scope cannot leave a request hanging`() =
        runTest {
            backgroundScope.cancel()
            val task = answerPhoneRequest(backgroundScope) { byteArrayOf(1) }
            runCurrent()
            assertTrue(task.isComplete)
            assertFalse(task.isSuccessful)
        }
}
