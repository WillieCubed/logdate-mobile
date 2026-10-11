package app.logdate.client.sync.v2.link

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

/** Tests [MessageClientLinkRequester]: an answer, a failure and a watch that never answers. */
class MessageClientLinkRequesterTest {
    private val client = mockk<MessageClient>()

    @Test
    fun `the watch's answer is returned`() =
        runTest {
            every { client.sendRequest("watch", "/logdate/link/offer", any()) } returns Tasks.forResult("ok".encodeToByteArray())

            val answer = MessageClientLinkRequester(client).request("watch", "/logdate/link/offer", ByteArray(0))

            assertContentEquals("ok".encodeToByteArray(), answer.getOrThrow())
        }

    @Test
    fun `a failed request is a failure with its cause`() =
        runTest {
            every { client.sendRequest(any(), any(), any()) } returns Tasks.forException(IllegalStateException("no listener"))

            val answer = MessageClientLinkRequester(client).request("watch", "/logdate/link/offer", ByteArray(0))

            assertIs<IllegalStateException>(answer.exceptionOrNull())
        }

    @Test
    fun `a watch that never answers times out`() =
        runTest {
            every { client.sendRequest(any(), any(), any()) } returns TaskCompletionSource<ByteArray>().task

            val answer = MessageClientLinkRequester(client, timeoutMs = 10).request("watch", "/logdate/link/offer", ByteArray(0))

            assertIs<TimeoutException>(answer.exceptionOrNull())
        }
}
