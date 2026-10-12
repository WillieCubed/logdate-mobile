package app.logdate.client.sync.v2.media

import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** sendFile's Task accepts the request; output closure proves that sending finished. */
class GooglePhoneMediaSender(
    private val channels: ChannelClient,
) {
    suspend fun send(
        channel: ChannelClient.Channel,
        uri: Uri,
        offset: Long,
        length: Long,
        current: suspend () -> Boolean,
    ) {
        if (!current()) throw IOException("The media exchange is no longer current")
        val finished = CompletableDeferred<Unit>()
        val callback = completionCallback(channel, finished)
        val disposed = AtomicBoolean()
        try {
            val registration = channels.registerChannelCallback(channel, callback)
            registration.addOnSuccessListener(Executor { it.run() }) {
                if (disposed.get()) {
                    try {
                        channels.unregisterChannelCallback(channel, callback)
                    } catch (e: Exception) {
                        Napier.w("Could not remove a late phone media callback", e)
                    }
                }
            }
            val accepted =
                withTimeoutOrNull(15_000) {
                    registration.await()
                    if (!current()) throw IOException("The media exchange changed before sending")
                    channels.sendFile(channel, uri, offset, length).await()
                    true
                } ?: false
            if (!accepted) throw IOException("The phone media send request timed out")
            while (!finished.isCompleted) {
                if (!current()) throw IOException("The media exchange changed during sending")
                delay(100)
            }
            finished.await()
            if (!current()) throw IOException("The media exchange changed during sending")
        } finally {
            disposed.set(true)
            withContext(NonCancellable) {
                try {
                    withTimeoutOrNull(5_000) { channels.unregisterChannelCallback(channel, callback).await() }
                } catch (e: Exception) {
                    Napier.w("Could not remove the phone media channel callback", e)
                }
            }
        }
    }

    private fun completionCallback(
        channel: ChannelClient.Channel,
        finished: CompletableDeferred<Unit>,
    ): ChannelClient.ChannelCallback =
        object : ChannelClient.ChannelCallback() {
            override fun onOutputClosed(
                closed: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int,
            ) {
                if (closed != channel) return
                if (closeReason == CLOSE_REASON_NORMAL && appSpecificErrorCode == 0) {
                    finished.complete(Unit)
                } else {
                    finished.completeExceptionally(IOException("Phone media output closed before completion"))
                }
            }

            override fun onChannelClosed(
                closed: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int,
            ) {
                if (closed == channel) finished.completeExceptionally(IOException("Phone media channel closed before completion"))
            }
        }
}
