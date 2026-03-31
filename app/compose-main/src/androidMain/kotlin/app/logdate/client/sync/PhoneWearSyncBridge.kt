package app.logdate.client.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.uuid.Uuid

interface PhoneWearSyncBridge {
    suspend fun publishNotesToWatch(sourceNodeId: String)

    suspend fun streamAudioToWatch(
        noteId: Uuid,
        sourceNodeId: String,
    )
}

interface PhoneWearTransport {
    suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
    ): Boolean

    suspend fun streamToNode(
        nodeId: String,
        channelPath: String,
        inputStream: InputStream,
    ): Boolean
}

interface PhoneAudioStreamOpener {
    fun open(mediaRef: String): InputStream?
}

fun interface PhoneWearPutDataRequestFactory {
    fun create(
        path: String,
        data: Map<String, String>,
    ): PutDataRequest
}

class DefaultPhoneWearSyncBridge(
    private val notesRepository: JournalNotesRepository,
    private val noteDataMapper: NoteDataMapper,
    private val transport: PhoneWearTransport,
    private val audioStreamOpener: PhoneAudioStreamOpener,
) : PhoneWearSyncBridge {
    override suspend fun publishNotesToWatch(sourceNodeId: String) {
        val notes = notesRepository.allNotesObserved.first()
        for (note in notes) {
            val sent =
                transport.putDataItem(
                    path = NoteDataMapper.notePath(note.uid),
                    data = noteDataMapper.toDataMap(note),
                )
            if (!sent) {
                Napier.w("Failed to publish note ${note.uid} to watch node $sourceNodeId")
            }
        }
    }

    override suspend fun streamAudioToWatch(
        noteId: Uuid,
        sourceNodeId: String,
    ) {
        val note = notesRepository.getNoteById(noteId) as? JournalNote.Audio
        if (note == null) {
            Napier.w("Watch requested audio for missing or non-audio note $noteId")
            return
        }

        val inputStream =
            audioStreamOpener.open(note.mediaRef) ?: run {
                Napier.w("Could not open audio stream for note $noteId")
                return
            }

        val sent =
            transport.streamToNode(
                nodeId = sourceNodeId,
                channelPath = WearAudioRequestPaths.audioTransferPath(noteId),
                inputStream = inputStream,
            )
        if (!sent) {
            Napier.w("Failed to stream audio note $noteId to watch")
        }
    }
}

class GooglePhoneWearTransport(
    private val dataClient: DataClient,
    private val channelClient: ChannelClient,
    private val putDataRequestFactory: PhoneWearPutDataRequestFactory = defaultPhoneWearPutDataRequestFactory,
) : PhoneWearTransport {
    constructor(context: Context) : this(
        dataClient = Wearable.getDataClient(context),
        channelClient = Wearable.getChannelClient(context),
    )

    override suspend fun putDataItem(
        path: String,
        data: Map<String, String>,
    ): Boolean =
        runCatching {
            val request = putDataRequestFactory.create(path, data)
            dataClient.putDataItem(request).await()
            true
        }.getOrElse { error ->
            Napier.w("Failed to put wear data item at path: $path", error)
            false
        }

    override suspend fun streamToNode(
        nodeId: String,
        channelPath: String,
        inputStream: InputStream,
    ): Boolean =
        runCatching {
            val channel = channelClient.openChannel(nodeId, channelPath).await()
            try {
                channelClient.getOutputStream(channel).await().use { outputStream ->
                    inputStream.use { source ->
                        source.copyTo(outputStream, bufferSize = 8192)
                    }
                }
            } finally {
                channelClient.close(channel).await()
            }
            true
        }.getOrElse { error ->
            Napier.w("Failed to stream wear audio on channel: $channelPath", error)
            false
        }

    private companion object {
        val defaultPhoneWearPutDataRequestFactory =
            PhoneWearPutDataRequestFactory { path, data ->
                PutDataMapRequest
                    .create(path)
                    .apply {
                        data.forEach { (key, value) ->
                            dataMap.putString(key, value)
                        }
                    }.asPutDataRequest()
            }
    }
}

class AndroidPhoneAudioStreamOpener(
    private val contentResolver: ContentResolver,
) : PhoneAudioStreamOpener {
    constructor(context: Context) : this(context.contentResolver)

    override fun open(mediaRef: String): InputStream? =
        runCatching {
            when {
                mediaRef.startsWith("content://") -> contentResolver.openInputStream(Uri.parse(mediaRef))
                mediaRef.startsWith("file://") -> Uri.parse(mediaRef).path?.let(::FileInputStream)
                mediaRef.startsWith("/") -> FileInputStream(File(mediaRef))
                else -> null
            }
        }.getOrElse { error ->
            Napier.w("Failed to open phone audio stream: $mediaRef", error)
            null
        }
}
