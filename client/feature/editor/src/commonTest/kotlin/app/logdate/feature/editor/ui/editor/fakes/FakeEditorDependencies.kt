package app.logdate.feature.editor.ui.editor.fakes

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.MediaPayload
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.shared.model.ActivityTimelineItem
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeJournalNotesRepository : JournalNotesRepository {
    private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())

    override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = notesFlow

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = notesFlow

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notesFlow.value.firstOrNull { it.uid == noteId }

    override suspend fun create(note: JournalNote): Uuid {
        notesFlow.value = notesFlow.value + note
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notesFlow.value = notesFlow.value.filterNot { it.uid == note.uid }
    }

    override suspend fun removeById(noteId: Uuid) {
        notesFlow.value = notesFlow.value.filterNot { it.uid == noteId }
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        create(note)
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {
        // No-op for tests
    }

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
}

class FakeJournalContentRepository : JournalContentRepository {
    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

    override suspend fun addContentToJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        // No-op for tests
    }

    override suspend fun removeContentFromJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        // No-op for tests
    }

    override suspend fun addContentToJournals(
        contentId: Uuid,
        journalIds: List<Uuid>,
    ) {
        // No-op for tests
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) {
        // No-op for tests
    }

    override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())
}

class FakeJournalRepository : JournalRepository {
    private val journals = MutableStateFlow<List<Journal>>(emptyList())

    override val allJournalsObserved: Flow<List<Journal>> = journals

    override fun observeJournalById(id: Uuid): Flow<Journal> =
        flow {
            journals.value.firstOrNull { it.id == id }?.let { emit(it) }
        }

    override suspend fun getJournalById(id: Uuid): Journal? = journals.value.firstOrNull { it.id == id }

    override suspend fun create(journal: Journal): Uuid {
        journals.value = journals.value + journal
        return journal.id
    }

    override suspend fun update(journal: Journal) {
        journals.value =
            journals.value.map { existing ->
                if (existing.id == journal.id) journal else existing
            }
    }

    override suspend fun delete(journalId: Uuid) {
        journals.value = journals.value.filterNot { it.id == journalId }
    }

    override suspend fun saveDraft(draft: EditorDraft) {
        // No-op for tests
    }

    override suspend fun getLatestDraft(): EditorDraft? = null

    override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

    override suspend fun getDraft(id: Uuid): EditorDraft? = null

    override suspend fun deleteDraft(id: Uuid) {
        // No-op for tests
    }
}

class FakeEntryDraftRepository : EntryDraftRepository {
    private val drafts = MutableStateFlow<List<EntryDraft>>(emptyList())
    private val deletionFailures = mutableSetOf<Uuid>()

    /**
     * Configures [deleteDraft] to throw for the given [draftId].
     */
    fun setDeletionFailure(draftId: Uuid) {
        deletionFailures.add(draftId)
    }

    override fun getDrafts(): Flow<List<EntryDraft>> = drafts

    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
        val draft = drafts.value.firstOrNull { it.id == uid }
        return if (draft != null) {
            flowOf(Result.success(draft))
        } else {
            flowOf(Result.failure(NoSuchElementException("Draft not found")))
        }
    }

    override suspend fun createDraft(notes: List<JournalNote>): Uuid {
        val now = Clock.System.now()
        val draft =
            EntryDraft(
                id = Uuid.random(),
                notes = notes,
                createdAt = now,
                updatedAt = now,
            )
        drafts.value = drafts.value + draft
        return draft.id
    }

    override suspend fun updateDraft(
        uid: Uuid,
        notes: List<JournalNote>,
    ): Uuid {
        val now = Clock.System.now()
        val existing = drafts.value.firstOrNull { it.id == uid }
        val updated =
            if (existing != null) {
                existing.copy(notes = notes, updatedAt = now)
            } else {
                EntryDraft(id = uid, notes = notes, createdAt = now, updatedAt = now)
            }
        drafts.value = drafts.value.filterNot { it.id == uid } + updated
        return uid
    }

    override suspend fun setPendingMedia(
        uid: Uuid,
        pendingMedia: List<PendingMediaRecord>,
    ) {
        val existing = drafts.value.firstOrNull { it.id == uid } ?: return
        val updated =
            existing.copy(
                pendingMedia = pendingMedia,
                updatedAt = Clock.System.now(),
            )
        drafts.value = drafts.value.filterNot { it.id == uid } + updated
    }

    override suspend fun deleteDraft(uid: Uuid) {
        if (uid in deletionFailures) {
            throw IllegalStateException("Simulated deletion failure for draft $uid")
        }
        drafts.value = drafts.value.filterNot { it.id == uid }
    }

    override suspend fun deleteAllDrafts() {
        drafts.value = emptyList()
    }

    override suspend fun deleteExpiredDrafts(maxAge: Duration): Int {
        val now = Clock.System.now()
        val expired = drafts.value.filter { now - it.updatedAt > maxAge }
        drafts.value = drafts.value - expired.toSet()
        return expired.size
    }
}

class FakeClientLocationProvider : ClientLocationProvider {
    private val defaultLocation =
        Location(
            latitude = 0.0,
            longitude = 0.0,
            altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
        )
    private val locationFlow =
        MutableSharedFlow<Location>(replay = 1).apply {
            tryEmit(defaultLocation)
        }

    override val currentLocation: SharedFlow<Location> = locationFlow

    override fun hasLocationPermission(): Boolean = true

    override suspend fun getCurrentLocation(): Location = defaultLocation

    override suspend fun refreshLocation() {
        locationFlow.emit(defaultLocation)
    }
}

class FakeActivityTimelineRepository : ActivityTimelineRepository {
    override val allItemsObserved: Flow<List<ActivityTimelineItem>> = flowOf(emptyList())

    override fun observeModelById(id: Uuid): Flow<ActivityTimelineItem> =
        flow {
            // No items to emit in tests
        }

    override suspend fun addActivity(item: ActivityTimelineItem) {
        // No-op for tests
    }

    override suspend fun removeActivity(item: ActivityTimelineItem) {
        // No-op for tests
    }

    override suspend fun updateActivity(item: ActivityTimelineItem) {
        // No-op for tests
    }

    override fun fetchActivitiesByType(type: String): Flow<List<ActivityTimelineItem>> = flowOf(emptyList())
}

class FakeLocationHistoryRepository : LocationHistoryRepository {
    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = emptyList()

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(emptyList())

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = emptyList()

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> = emptyList()

    override suspend fun getLastLocation(): LocationHistoryItem? = null

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(null)

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getLocationCount(): Int = 0
}

class FakeMediaManager : MediaManager {
    override suspend fun getMedia(uri: String): MediaObject =
        MediaObject.Image(
            uri = uri,
            size = 0,
            name = "media",
            timestamp = Clock.System.now(),
        )

    override suspend fun exists(mediaId: String): Boolean = false

    override suspend fun getRecentMedia(limit: Int): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun queryMediaByDate(
        start: Instant,
        end: Instant,
    ): Flow<List<MediaObject>> = flowOf(emptyList())

    override suspend fun addToDefaultCollection(uri: String) {
        // No-op for tests
    }

    override suspend fun readMedia(uri: String): MediaPayload =
        MediaPayload(
            fileName = "media.bin",
            mimeType = "application/octet-stream",
            sizeBytes = 0,
            data = ByteArray(0),
        )

    override suspend fun saveMedia(payload: MediaPayload): String = "file:///tmp/${payload.fileName}"

    override suspend fun saveMediaFromFile(
        sourceFilePath: String,
        fileName: String,
        mimeType: String,
    ): String = "file:///tmp/$fileName"
}

class FakeLocationTrackingSettingsRepository : LocationTrackingSettingsRepository {
    private val settings = LocationTrackingSettings()

    override suspend fun getSettings(): LocationTrackingSettings = settings

    override fun observeSettings(): kotlinx.coroutines.flow.Flow<LocationTrackingSettings> = flowOf(settings)

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        // No-op for tests
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        // No-op for tests
    }

    override suspend fun setTrackingInterval(intervalMinutes: Long) {
        // No-op for tests
    }
}
