package app.logdate.client.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.remote.RemoteCameraActivity
import app.logdate.client.remote.RemoteCameraSessionController
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.feature.editor.ui.camera.CameraRemoteCommand
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Receives data items from the paired Wear OS watch via the Data Layer API.
 *
 * Handles notes, journals, journal-content associations, health snapshots, and the audio files
 * that go with watch audio notes. Notes and audio go through [WatchNoteIngestor], which queues
 * them for cloud backup once they are complete and tells the watch they arrived. Journals and
 * associations use sync-aware repository methods so they don't re-trigger outbound sync.
 *
 * For new notes, a notification is posted so the user can tap to expand the note
 * in the editor (entry handoff).
 *
 * Koin resilience: If DI is not ready when data arrives, retries up to
 * [MAX_KOIN_RETRIES] times with a short delay before dropping the event.
 */
open class PhoneDataLayerListenerService : WearableListenerService() {
    protected open val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noteDataMapper = NoteDataMapper()
    private val journalDataMapper = JournalDataMapper()
    private val associationDataMapper = AssociationDataMapper()
    private val healthSnapshotDataMapper = HealthSnapshotDataMapper()

    companion object {
        private const val PATH_CAMERA_OPEN = "/logdate/camera/open"
        private const val PATH_CAMERA_CAPTURE = "/logdate/camera/capture"
        private const val PATH_CAMERA_SWITCH = "/logdate/camera/switch"
        private const val PATH_CAMERA_SELECT = "/logdate/camera/select"
        private const val PATH_CAMERA_CLOSE = "/logdate/camera/close"
        private const val CAMERA_SELECT_FRONT = "front"
        private const val CAMERA_SELECT_BACK = "back"
        private const val CAMERA_SELECT_DEVICE_PREFIX = "device:"
        private const val MAX_KOIN_RETRIES = 3
        private const val KOIN_RETRY_DELAY_MS = 500L
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        serviceScope.launch {
            when {
                messageEvent.path == PATH_CAMERA_OPEN -> {
                    Napier.d("Watch requested camera open")
                    try {
                        launchCamera()
                    } catch (e: Exception) {
                        Napier.w("Failed to open camera from watch request", e)
                    }
                }

                messageEvent.path == PATH_CAMERA_CAPTURE -> {
                    Napier.d("Watch requested photo capture")
                    sendCameraCommand(CameraRemoteCommand.Capture)
                }

                messageEvent.path == PATH_CAMERA_SWITCH -> {
                    Napier.d("Watch requested camera switch")
                    sendCameraCommand(CameraRemoteCommand.SwitchBuiltInCamera)
                }

                messageEvent.path == PATH_CAMERA_SELECT -> {
                    val selection = messageEvent.data.decodeToString()
                    Napier.d("Watch requested camera selection: $selection")
                    when {
                        selection == CAMERA_SELECT_FRONT ->
                            sendCameraCommand(
                                CameraRemoteCommand.SelectCameraCategory(MediaDeviceCategory.FRONT_CAMERA),
                            )
                        selection == CAMERA_SELECT_BACK ->
                            sendCameraCommand(
                                CameraRemoteCommand.SelectCameraCategory(MediaDeviceCategory.BACK_CAMERA),
                            )
                        selection.startsWith(CAMERA_SELECT_DEVICE_PREFIX) ->
                            sendCameraCommand(
                                CameraRemoteCommand.SelectCameraDevice(
                                    selection.removePrefix(CAMERA_SELECT_DEVICE_PREFIX),
                                ),
                            )
                        else -> Napier.w("Unknown remote camera selection: $selection")
                    }
                }

                messageEvent.path == PATH_CAMERA_CLOSE -> {
                    Napier.d("Watch requested camera close")
                    sendCameraCommand(CameraRemoteCommand.Close)
                }

                messageEvent.path == WearAudioRequestPaths.SYNC_REQUEST_PATH -> {
                    val syncBridge = resolvePhoneWearSyncBridge() ?: return@launch
                    syncBridge.publishNotesToWatch(sourceNodeId = messageEvent.sourceNodeId)
                }

                WearAudioRequestPaths.isAudioRequestPath(messageEvent.path) -> {
                    val syncBridge = resolvePhoneWearSyncBridge() ?: return@launch
                    val noteId =
                        runCatching {
                            WearAudioRequestPaths.noteIdFromAudioRequestPath(messageEvent.path)
                        }.getOrElse { error ->
                            Napier.w("Invalid audio request path: ${messageEvent.path}", error)
                            return@launch
                        }
                    syncBridge.streamAudioToWatch(
                        noteId = noteId,
                        sourceNodeId = messageEvent.sourceNodeId,
                    )
                }

                else -> {
                    Napier.d("Unknown message path: ${messageEvent.path}")
                }
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (!WearAudioRequestPaths.isAudioTransferPath(channel.path)) {
            Napier.d("Ignoring channel at unknown path: ${channel.path}")
            return
        }
        serviceScope.launch { receiveAudioChannel(channel) }
    }

    /**
     * Streams the watch's audio file into the ingestor. Runs inside [NonCancellable] so a service
     * teardown mid-transfer does not leave a half-received file for the watch to retry.
     */
    private suspend fun receiveAudioChannel(channel: ChannelClient.Channel) =
        withContext(NonCancellable) {
            val noteId =
                runCatching { WearAudioRequestPaths.noteIdFromAudioTransferPath(channel.path) }
                    .getOrElse { error ->
                        Napier.w("Invalid audio transfer path: ${channel.path}", error)
                        closeChannel(channel)
                        return@withContext
                    }
            val ingestor = resolveSyncDependencies()?.noteIngestor
            if (ingestor == null) {
                Napier.e("Koin unavailable, dropping audio from the watch for note $noteId")
                closeChannel(channel)
                return@withContext
            }

            try {
                openChannelInput(channel).use { input -> ingestor.onAudioBytes(noteId, input) }
            } catch (e: Exception) {
                Napier.w("Failed to receive audio from the watch for note $noteId", e)
            } finally {
                closeChannel(channel)
            }
        }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot events before they are recycled by the system
        val events = mutableListOf<PhoneDataLayerSnapshotEvent>()
        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue
            val stringMap = extractStringMap(event)
            events.add(PhoneDataLayerSnapshotEvent(path, stringMap))
        }

        if (events.isEmpty()) return

        serviceScope.launch {
            processSnapshotEvents(events)
        }
    }

    protected open suspend fun processSnapshotEvents(events: List<PhoneDataLayerSnapshotEvent>) {
        val dependencies = resolveSyncDependencies()
        if (dependencies == null) {
            Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries, dropping ${events.size} data events")
            return
        }

        for (event in events) {
            processSnapshotEvent(event, dependencies)
        }
    }

    protected open suspend fun resolveSyncDependencies(): PhoneDataLayerSyncDependencies? {
        val koin = resolveKoin() ?: return null
        return PhoneDataLayerSyncDependencies(
            noteIngestor = koin.get(),
            journalRepository = koin.get(),
            contentRepository = koin.getOrNull(),
            journalContentDao = koin.getOrNull(),
            healthSnapshotDao = koin.get(),
        )
    }

    /**
     * Processes a single data event. Runs inside [NonCancellable] to ensure
     * database writes complete even if the service is destroyed mid-flight.
     */
    protected open suspend fun processSnapshotEvent(
        event: PhoneDataLayerSnapshotEvent,
        dependencies: PhoneDataLayerSyncDependencies,
    ) = withContext(NonCancellable) {
        val path = event.path

        try {
            when {
                NoteDataMapper.isDeletePath(path) -> handleNoteDelete(path, dependencies.noteIngestor)
                NoteDataMapper.isNotePath(path) -> handleNoteSync(event, path, dependencies.noteIngestor)
                JournalDataMapper.isDeletePath(path) -> handleJournalDelete(path, dependencies.journalRepository)
                JournalDataMapper.isJournalPath(path) -> handleJournalSync(event, path, dependencies.journalRepository)
                AssociationDataMapper.isDeletePath(path) -> handleAssociationDelete(path, dependencies)
                AssociationDataMapper.isAssociationPath(path) -> handleAssociationSync(event, path, dependencies)
                HealthSnapshotDataMapper.isHealthPath(path) -> handleHealthSync(event, path, dependencies.healthSnapshotDao)
            }
        } catch (e: Exception) {
            Napier.e("Unhandled error processing sync event at path: $path", e)
        }
    }

    private suspend fun handleNoteDelete(
        path: String,
        ingestor: WatchNoteIngestor,
    ) {
        val noteId =
            try {
                NoteDataMapper.noteIdFromPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid note ID in delete path: $path", e)
                return
            }

        Napier.d("Received delete signal from watch for note: $noteId")
        try {
            ingestor.onNoteDeleted(noteId)
        } catch (e: Exception) {
            Napier.w("Failed to delete synced note from watch: $noteId", e)
        }
    }

    private suspend fun handleNoteSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        ingestor: WatchNoteIngestor,
    ) {
        try {
            ingestor.onNoteMetadata(event.data)
        } catch (e: Exception) {
            Napier.w("Failed to process synced note from watch at path: $path", e)
        }
    }

    private suspend fun handleJournalDelete(
        path: String,
        journalRepository: JournalRepository,
    ) {
        val journalId =
            try {
                JournalDataMapper.journalIdFromPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid journal ID in delete path: $path", e)
                return
            }

        Napier.d("Received delete signal from watch for journal: $journalId")
        try {
            if (journalRepository is SyncableJournalRepository) {
                journalRepository.deleteFromSync(journalId)
            } else {
                journalRepository.delete(journalId)
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete synced journal from watch: $journalId", e)
        }
    }

    private suspend fun handleJournalSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        journalRepository: JournalRepository,
    ) {
        try {
            val journal = journalDataMapper.fromDataMap(event.data)
            Napier.d("Received journal from watch: ${journal.id}")
            if (journalRepository is SyncableJournalRepository) {
                // createFromSync uses upsert — safe for duplicates and updates
                journalRepository.createFromSync(journal)
            } else {
                journalRepository.create(journal)
            }
        } catch (e: Exception) {
            Napier.w("Failed to process synced journal from watch at path: $path", e)
        }
    }

    private suspend fun handleAssociationDelete(
        path: String,
        dependencies: PhoneDataLayerSyncDependencies,
    ) {
        val (journalId, contentId) =
            try {
                AssociationDataMapper.idsFromPath(path)
            } catch (e: Exception) {
                Napier.w("Invalid association path: $path", e)
                return
            }

        Napier.d("Received association delete from watch: journal=$journalId, content=$contentId")
        try {
            val contentRepo = dependencies.contentRepository
            if (contentRepo != null) {
                contentRepo.removeContentFromJournalFromSync(contentId, journalId)
            } else {
                val dao = requireNotNull(dependencies.journalContentDao) { "JournalContentDao unavailable" }
                dao.removeContentFromJournal(journalId, contentId)
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete synced association: $path", e)
        }
    }

    private suspend fun handleAssociationSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        dependencies: PhoneDataLayerSyncDependencies,
    ) {
        try {
            val (journalId, contentId) = associationDataMapper.fromDataMap(event.data)
            Napier.d("Received association from watch: journal=$journalId, content=$contentId")
            val contentRepo = dependencies.contentRepository
            if (contentRepo != null) {
                contentRepo.addContentToJournalFromSync(contentId, journalId)
            } else {
                val dao = requireNotNull(dependencies.journalContentDao) { "JournalContentDao unavailable" }
                dao.addContentToJournal(JournalContentEntityLink(journalId, contentId))
            }
        } catch (e: Exception) {
            Napier.w("Failed to process synced association from watch at path: $path", e)
        }
    }

    private suspend fun handleHealthSync(
        event: PhoneDataLayerSnapshotEvent,
        path: String,
        healthSnapshotDao: HealthSnapshotDao,
    ) {
        try {
            val syncData = healthSnapshotDataMapper.fromDataMap(event.data)
            Napier.d("Received health snapshot from watch: ${syncData.id}")
            healthSnapshotDao.insert(
                HealthSnapshotEntity(
                    id = syncData.id,
                    noteId = syncData.noteId,
                    heartRateBpm = syncData.heartRateBpm,
                    heartRateVariabilityMs = syncData.heartRateVariabilityMs,
                    stepCount = syncData.stepCount,
                    stressLevel = syncData.stressLevel,
                    cumulativeCalories = syncData.cumulativeCalories,
                    timestamp = syncData.timestamp,
                    source = syncData.source,
                ),
            )
        } catch (e: Exception) {
            Napier.w("Failed to process synced health snapshot from watch at path: $path", e)
        }
    }

    /**
     * Resolves the Koin instance, retrying with a short delay if DI isn't ready yet.
     */
    private suspend fun resolveKoin(): org.koin.core.Koin? {
        repeat(MAX_KOIN_RETRIES) { attempt ->
            try {
                return org.koin.java.KoinJavaComponent
                    .getKoin()
            } catch (e: Exception) {
                if (attempt < MAX_KOIN_RETRIES - 1) {
                    Napier.d("Koin not ready (attempt ${attempt + 1}/$MAX_KOIN_RETRIES), retrying...")
                    delay(KOIN_RETRY_DELAY_MS)
                }
            }
        }
        return null
    }

    protected open suspend fun resolvePhoneWearSyncBridge(): PhoneWearSyncBridge? = resolveKoin()?.get()

    protected open suspend fun openChannelInput(channel: ChannelClient.Channel): InputStream =
        Wearable.getChannelClient(this).getInputStream(channel).await()

    protected open suspend fun closeChannel(channel: ChannelClient.Channel) {
        runCatching { Wearable.getChannelClient(this).close(channel).await() }
            .onFailure { error -> Napier.w("Failed to close audio channel ${channel.path}", error) }
    }

    protected open fun launchCamera() {
        startActivity(RemoteCameraActivity.createIntent(this))
    }

    protected open fun sendCameraCommand(command: CameraRemoteCommand): Boolean = RemoteCameraSessionController.send(command)

    private fun extractStringMap(event: com.google.android.gms.wearable.DataEvent): Map<String, String> {
        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val stringMap = mutableMapOf<String, String>()
        for (key in dataMap.keySet()) {
            if (key.startsWith("_")) continue
            dataMap.getString(key)?.let { stringMap[key] = it }
        }
        return stringMap
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

/**
 * Snapshot of a data event's path and payload.
 * Created before [DataEventBuffer] is recycled by the system.
 */
data class PhoneDataLayerSnapshotEvent(
    val path: String,
    val data: Map<String, String>,
)

data class PhoneDataLayerSyncDependencies(
    val noteIngestor: WatchNoteIngestor,
    val journalRepository: JournalRepository,
    val contentRepository: SyncableJournalContentRepository? = null,
    val journalContentDao: JournalContentDao? = null,
    val healthSnapshotDao: HealthSnapshotDao,
)
