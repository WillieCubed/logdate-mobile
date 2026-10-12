package app.logdate.client.sync.v2.media

import android.net.Uri
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GooglePhoneMediaSenderTest {
    private val channels = mockk<ChannelClient>()
    private val channel = mockk<ChannelClient.Channel>()
    private val uri = mockk<Uri>()
    private val callback = slot<ChannelClient.ChannelCallback>()
    private val sender = GooglePhoneMediaSender(channels)

    init {
        every { channels.registerChannelCallback(channel, capture(callback)) } returns Tasks.forResult<Void>(null)
        every { channels.unregisterChannelCallback(channel, any()) } returns Tasks.forResult(true)
        every { channels.sendFile(channel, uri, 5L, 6L) } returns Tasks.forResult<Void>(null)
    }

    @Test fun `accepted send waits for normal output closure before returning`() =
        runTest {
            val send = async(start = CoroutineStart.UNDISPATCHED) { sender.send(channel, uri, 5L, 6L) { true } }
            assertFalse(send.isCompleted)
            callback.captured.onOutputClosed(channel, ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL, 0)
            advanceTimeBy(100)
            runCurrent()
            send.await()
            verify(exactly = 1) { channels.sendFile(channel, uri, 5L, 6L) }
            verify { channels.unregisterChannelCallback(channel, callback.captured) }
        }

    @Test fun `abnormal output closure fails the transfer`() =
        runTest {
            val send =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { sender.send(channel, uri, 5L, 6L) { true } }
                }
            callback.captured.onOutputClosed(channel, ChannelClient.ChannelCallback.CLOSE_REASON_DISCONNECTED, 0)
            advanceTimeBy(100)
            runCurrent()
            kotlin.test.assertIs<IOException>(send.await().exceptionOrNull())
            verify { channels.unregisterChannelCallback(channel, callback.captured) }
        }

    @Test fun `revocation stops waiting for the file to finish`() =
        runTest {
            var current = true
            val send =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { sender.send(channel, uri, 5L, 6L) { current } }
                }
            current = false
            advanceTimeBy(100)
            runCurrent()
            kotlin.test.assertIs<IOException>(send.await().exceptionOrNull())
            verify { channels.unregisterChannelCallback(channel, callback.captured) }
        }

    @Test fun `a stale exchange never starts a file transfer`() =
        runTest {
            assertFailsWith<IOException> { sender.send(channel, uri, 5L, 6L) { false } }
            verify(exactly = 0) { channels.sendFile(any(), any(), any(), any()) }
        }

    @Test fun `a stalled send request fails without waiting indefinitely`() =
        runTest {
            every { channels.sendFile(channel, uri, 5L, 6L) } returns TaskCompletionSource<Void>().task
            val send =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { sender.send(channel, uri, 5L, 6L) { true } }
                }
            try {
                advanceTimeBy(15_000)
                runCurrent()
                kotlin.test.assertTrue(send.isCompleted)
                kotlin.test.assertIs<IOException>(send.await().exceptionOrNull())
            } finally {
                send.cancelAndJoin()
            }
            verify { channels.unregisterChannelCallback(channel, callback.captured) }
        }

    @Test fun `late callback registration is cleaned up after a setup timeout`() =
        runTest {
            val registration = TaskCompletionSource<Void>()
            every { channels.registerChannelCallback(channel, capture(callback)) } returns registration.task
            val send =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { sender.send(channel, uri, 5L, 6L) { true } }
                }
            advanceTimeBy(15_000)
            runCurrent()
            kotlin.test.assertIs<IOException>(send.await().exceptionOrNull())
            verify(exactly = 1) { channels.unregisterChannelCallback(channel, callback.captured) }
            registration.setResult(null)
            verify(exactly = 2) { channels.unregisterChannelCallback(channel, callback.captured) }
        }
}
