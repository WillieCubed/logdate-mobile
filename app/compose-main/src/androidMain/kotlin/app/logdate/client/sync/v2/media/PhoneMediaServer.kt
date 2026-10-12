package app.logdate.client.sync.v2.media

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaInfoRequest
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeRequest
import app.logdate.client.sync.datalayer.v2.PhoneMediaRangeTicket
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Serves only current linked watches; requests never supply a local filesystem path. */
class PhoneMediaServer(
    private val peers: PhoneDeliveryPeers,
    private val notes: JournalNotesRepository,
    private val filePath: (String) -> String?,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Published(
        val media: PhoneMediaDescriptor,
        val note: JournalNote.Audio,
        val file: File,
    )

    private data class Ticket(
        val request: PhoneMediaRangeRequest,
        val published: Published,
        val issuedAt: Long,
    )

    private val lock = Mutex()
    private val tickets = mutableMapOf<String, Ticket>()

    suspend fun describe(
        node: String,
        request: PhoneMediaInfoRequest,
    ): PhoneMediaDescriptor? = snapshot(node, request)?.media

    suspend fun prepare(
        node: String,
        request: PhoneMediaRangeRequest,
    ): PhoneMediaRangeTicket? {
        if (request.schema != 1 || request.media.schema != 1 || request.offset !in 0..request.media.sizeBytes) return null
        val media = request.media
        val published =
            snapshot(node, PhoneMediaInfoRequest(context = media.context, noteId = media.noteId, contentVersion = media.contentVersion))
                ?: return null
        if (published.media != media) return null
        return lock.withLock {
            val now = nowMs()
            tickets.entries.removeAll { now - it.value.issuedAt > TICKET_LIFETIME_MS }
            if (tickets.size >= MAX_TICKETS || !current(published)) return@withLock null
            val path = WearMediaPaths.CHANNEL_PREFIX + Uuid.random()
            tickets[path] = Ticket(request, published, now)
            PhoneMediaRangeTicket(media = media, offset = request.offset, channelPath = path)
        }
    }

    suspend fun serve(
        node: String,
        path: String,
        send: suspend (File, Long, Long, suspend () -> Boolean) -> Unit,
    ): Boolean {
        val ticket =
            lock.withLock {
                val found = tickets[path] ?: return@withLock null
                if (found.request.media.context.watchNode != node) return@withLock null
                tickets.remove(path)
            } ?: return false
        if (nowMs() - ticket.issuedAt > TICKET_LIFETIME_MS || !current(ticket.published)) return false
        val media = ticket.request.media
        val latest =
            snapshot(node, PhoneMediaInfoRequest(context = media.context, noteId = media.noteId, contentVersion = media.contentVersion))
        if (latest != ticket.published) return false
        send(ticket.published.file, ticket.request.offset, media.sizeBytes - ticket.request.offset) { current(ticket.published) }
        return current(ticket.published)
    }

    private suspend fun snapshot(
        node: String,
        request: PhoneMediaInfoRequest,
    ): Published? {
        if (request.schema != 1 || request.contentVersion < 0) return null
        val id = Uuid.parseOrNull(request.noteId)?.takeIf { it.toString() == request.noteId } ?: return null
        val peer = peers.current(node) ?: return null
        if (peer != request.context || !peer.isValid()) return null
        val note = notes.getNoteById(id) as? JournalNote.Audio ?: return null
        if (note.lastUpdated.toEpochMilliseconds() != request.contentVersion) return null
        val file = filePath(note.mediaRef)?.let(::File)?.takeIf { it.isFile && it.canRead() } ?: return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!peers.isCurrent(peer)) return null
                    val count = input.read(buffer)
                    if (count == -1) break
                    digest.update(buffer, 0, count)
                    size += count
                }
            }
            if (size <= 0 || file.length() != size || !sameNote(peer, note)) return null
            Published(
                PhoneMediaDescriptor(
                    context = peer,
                    noteId = request.noteId,
                    contentVersion = request.contentVersion,
                    sizeBytes = size,
                    sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                    mediaRef = note.mediaRef,
                ),
                note,
                file,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not prepare phone audio for the linked watch", e)
            null
        }
    }

    private suspend fun current(published: Published): Boolean =
        sameNote(published.media.context, published.note) && published.file.isFile && published.file.length() == published.media.sizeBytes

    private suspend fun sameNote(
        peer: PeerDeliveryContext,
        note: JournalNote.Audio,
    ): Boolean = peers.isCurrent(peer) && notes.getNoteById(note.uid) == note && peers.isCurrent(peer)

    private companion object {
        const val TICKET_LIFETIME_MS = 60_000L
        const val MAX_TICKETS = 128
    }
}
