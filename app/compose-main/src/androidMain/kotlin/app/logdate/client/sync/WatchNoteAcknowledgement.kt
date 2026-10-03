package app.logdate.client.sync

import android.content.Context
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.watch.WatchNodeSelector
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Delivers a message to the watches running LogDate. Returns false when no watch received it. */
fun interface WatchMessageSender {
    suspend fun send(
        path: String,
        data: ByteArray,
    ): Boolean
}

/**
 * Tells the watch a note is stored on the phone, and which version: the watch clears the note only
 * if it has not changed since. Watches that predate versioned acknowledgements ignore the payload.
 */
class MessageWatchNoteAcknowledger(
    private val sender: WatchMessageSender,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        val payload = NoteAckPayload.of(lastUpdated).encode()
        if (!sender.send(WearAudioRequestPaths.noteAckPath(noteId), payload)) {
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

    override suspend fun send(
        path: String,
        data: ByteArray,
    ): Boolean {
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
        return watches.count { watch -> deliver(watch, path, data) } > 0
    }

    private suspend fun deliver(
        watch: Node,
        path: String,
        data: ByteArray,
    ): Boolean =
        try {
            messageClient.sendMessage(watch.id, path, data).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not message watch ${watch.displayName} at $path", e)
            false
        }
}
