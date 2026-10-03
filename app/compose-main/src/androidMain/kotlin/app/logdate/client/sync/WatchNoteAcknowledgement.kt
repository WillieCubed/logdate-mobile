package app.logdate.client.sync

import android.content.Context
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.uuid.Uuid

/** Delivers a payload-free message to the paired watch. Returns false when nothing was sent. */
fun interface WatchMessageSender {
    suspend fun send(path: String): Boolean
}

class MessageWatchNoteAcknowledger(
    private val sender: WatchMessageSender,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(noteId: Uuid) {
        if (!sender.send(WearAudioRequestPaths.noteAckPath(noteId))) {
            Napier.w("Watch did not receive the acknowledgement for note $noteId; it will retry")
        }
    }
}

/**
 * Messages every reachable watch running LogDate, each on its own, so a watch that cannot be reached
 * does not stop the message reaching another.
 */
class GoogleWatchMessageSender(
    private val watchNodeSelector: WatchNodeSelector,
    private val messageClient: MessageClient,
) : WatchMessageSender {
    constructor(context: Context) : this(
        watchNodeSelector = WatchNodeSelector(Wearable.getCapabilityClient(context)),
        messageClient = Wearable.getMessageClient(context),
    )

    override suspend fun send(path: String): Boolean {
        val watches =
            try {
                watchNodeSelector.reachableWatches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not look up reachable watches for $path", e)
                return false
            }
        if (watches.isEmpty()) {
            Napier.w("No reachable watch runs LogDate; $path was not sent")
            return false
        }
        return watches.count { watch -> deliver(watch, path) } > 0
    }

    private suspend fun deliver(
        watch: Node,
        path: String,
    ): Boolean =
        try {
            messageClient.sendMessage(watch.id, path, ByteArray(0)).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not message watch ${watch.displayName} at $path", e)
            false
        }
}
