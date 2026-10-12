package app.logdate.wear.sync.v2.media

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PhoneMediaDescriptor
import app.logdate.client.sync.datalayer.v2.PhoneMediaInfoRequest
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearMediaPaths
import app.logdate.wear.playback.WatchMediaCache
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Keeps phone media identity separate from a note's logical reference and the watch's recordings. */
class WatchPhoneMedia(
    private val peers: WatchDeliveryPeer,
    private val notes: JournalNotesRepository,
    private val cache: WatchMediaCache,
    private val storage: KeyValueStorage,
    private val request: suspend (PeerDeliveryContext, String, ByteArray) -> Result<ByteArray>,
    private val source: (PeerDeliveryContext) -> MediaRangeSource,
    private val supported: suspend (PeerDeliveryContext) -> Boolean,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Cached(
        val identity: WatchMediaIdentity,
        val file: File,
    )

    private data class Permit(
        val identity: WatchMediaIdentity,
        val issuedAt: Long,
    )

    private val permits = linkedMapOf<String, Permit>()
    private val permitLock = Any()
    private val downloads = Mutex()

    suspend fun prepare(note: JournalNote.Audio): String {
        val peer = peers.current() ?: throw IOException("No verified phone is linked")
        ensureCurrent(note, peer)
        val media = cached(note, peer)?.identity ?: describe(note, peer)
        ensureCurrent(note, peer)
        return synchronized(permitLock) {
            val now = nowMs()
            permits.entries.removeAll { now - it.value.issuedAt > PERMIT_LIFETIME_MS }
            if (permits.size >= MAX_PERMITS) permits.remove(permits.keys.first())
            val token = Uuid.random().toString()
            permits[token] = Permit(media, now)
            SCHEME + token
        }
    }

    suspend fun download(
        note: JournalNote.Audio,
        expectedPeer: PeerDeliveryContext? = null,
        allowed: suspend () -> Boolean = { true },
    ): File {
        val peer = expectedPeer ?: peers.current() ?: throw IOException("No verified phone is linked")

        suspend fun checkAllowed() {
            ensureCurrent(note, peer)
            if (!allowed()) throw IOException("The phone audio is no longer eligible for download")
        }
        checkAllowed()
        return downloads.withLock {
            checkAllowed()
            cached(note, peer)?.let { return@withLock it.file }
            val media = describe(note, peer)
            checkAllowed()
            cache.evict(note.uid)
            val file =
                cache.store(note.uid, "m4a") { path, beforeWrite ->
                    val copied =
                        MediaStreamClient(source(peer), ::isCurrent).copyTo(media, File(path)) { count ->
                            checkAllowed()
                            beforeWrite(count)
                        }
                    checkAllowed()
                    copied
                } ?: throw IOException("The phone audio download was invalidated")
            checkAllowed()
            storage.putString(cacheKey(note.uid), WearJson.encode(PhoneMediaDescriptor.serializer(), media.asDescriptor()))
            checkAllowed()
            if (cache.cachedFile(note.uid) != file) throw IOException("The phone audio was evicted before playback")
            file
        }
    }

    suspend fun identity(uri: String): WatchMediaIdentity {
        val token = uri.removePrefix(SCHEME)
        if (!uri.startsWith(SCHEME) || Uuid.parseOrNull(token)?.toString() != token) throw IOException("Invalid watch audio URI")
        val permit = synchronized(permitLock) { permits[token] } ?: throw IOException("The watch audio session expired")
        if (nowMs() - permit.issuedAt > PERMIT_LIFETIME_MS ||
            !isCurrent(permit.identity)
        ) {
            throw IOException("The watch audio session changed")
        }
        return permit.identity
    }

    suspend fun isCurrent(identity: WatchMediaIdentity): Boolean {
        currentCoroutineContext().ensureActive()
        if (!peers.isCurrent(identity.peer)) return false
        val note = notes.getNoteById(identity.noteId) as? JournalNote.Audio ?: return false
        return note.lastUpdated.toEpochMilliseconds() == identity.contentVersion &&
            note.mediaRef == identity.mediaRef &&
            peers.isCurrent(identity.peer)
    }

    fun source(identity: WatchMediaIdentity): MediaRangeSource =
        MediaRangeSource { request ->
            if (request.identity != identity || !isCurrent(identity)) throw IOException("The watch audio range changed")
            val note = notes.getNoteById(identity.noteId) as? JournalNote.Audio ?: throw IOException("The phone note was removed")
            val saved = cached(note, identity.peer)
            if (saved?.identity == identity) FileMediaRangeSource(saved.file).open(request) else source(identity.peer).open(request)
        }

    private suspend fun ensureCurrent(
        note: JournalNote.Audio,
        peer: PeerDeliveryContext,
    ) {
        currentCoroutineContext().ensureActive()
        val current = notes.getNoteById(note.uid) as? JournalNote.Audio
        if (current?.mediaRef != note.mediaRef || current.lastUpdated != note.lastUpdated || !peers.isCurrent(peer)) {
            throw IOException("The phone audio note or exchange changed")
        }
    }

    private suspend fun describe(
        note: JournalNote.Audio,
        peer: PeerDeliveryContext,
    ): WatchMediaIdentity {
        if (!supported(peer)) throw IOException("The linked phone does not support media ranges")
        val bytes =
            withTimeoutOrNull(15_000) {
                request(
                    peer,
                    WearMediaPaths.INFO,
                    WearJson.encodeToBytes(
                        PhoneMediaInfoRequest.serializer(),
                        PhoneMediaInfoRequest(
                            context = peer,
                            noteId = note.uid.toString(),
                            contentVersion = note.lastUpdated.toEpochMilliseconds(),
                        ),
                    ),
                ).getOrThrow()
            } ?: throw IOException("The phone did not describe its audio in time")
        if (bytes.size > 8192) throw IOException("The phone audio descriptor is too large")
        val media =
            (WearJson.decode(bytes, 1, PhoneMediaDescriptor.serializer()) as? Decoded.Supported)?.value
                ?: throw IOException("The phone audio descriptor is unsupported")
        if (!matches(media, note, peer)) throw IOException("The phone audio descriptor does not match the note")
        ensureCurrent(note, peer)
        return media.identity()
    }

    private suspend fun cached(
        note: JournalNote.Audio,
        peer: PeerDeliveryContext,
    ): Cached? {
        val saved = storage.getString(cacheKey(note.uid)) ?: return null
        val media = (WearJson.decode(saved, 1, PhoneMediaDescriptor.serializer()) as? Decoded.Supported)?.value ?: return null
        if (!matches(media, note, peer)) return null
        val file = cache.cachedFile(note.uid) ?: return null
        if (file.length() != media.sizeBytes) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != media.sha256) return null
        ensureCurrent(note, peer)
        return Cached(media.identity(), file)
    }

    private fun matches(
        media: PhoneMediaDescriptor,
        note: JournalNote.Audio,
        peer: PeerDeliveryContext,
    ): Boolean =
        media.schema == 1 &&
            media.context == peer &&
            media.noteId == note.uid.toString() &&
            media.contentVersion == note.lastUpdated.toEpochMilliseconds() &&
            media.mediaRef == note.mediaRef &&
            media.sizeBytes > 0 &&
            media.sha256.matches(Regex("[0-9a-f]{64}"))

    private fun PhoneMediaDescriptor.identity() =
        WatchMediaIdentity(context, Uuid.parse(noteId), contentVersion, sizeBytes, sha256, mediaRef)

    private fun cacheKey(id: Uuid) = "watch.media.verified.v1.$id"

    companion object {
        const val SCHEME = "logdate-watch-media://"
        private const val MAX_PERMITS = 64
        private const val PERMIT_LIFETIME_MS = 6L * 60 * 60 * 1000
    }
}
