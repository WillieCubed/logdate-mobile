package app.logdate.client.domain.export.support

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.Place
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

// In-memory repositories and stubs shared by the export and restore tests.

/**
 * In-memory journal repository that supports both export-side reads and import-side writes.
 */
internal class RoundTripJournalRepository : JournalRepository {
    private val journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
    private val journals = mutableMapOf<Uuid, Journal>()
    private val drafts = mutableListOf<EditorDraft>()

    var testJournals: List<Journal> = emptyList()
        set(value) {
            field = value
            value.forEach { journals[it.id] = it }
            journalsFlow.value = value
        }

    var testDrafts: List<EditorDraft> = emptyList()
        set(value) {
            field = value
            drafts.clear()
            drafts.addAll(value)
        }

    override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

    override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(journals[id] ?: Journal(id = id))

    override suspend fun getJournalById(id: Uuid): Journal? = journals[id]

    override suspend fun create(journal: Journal): Uuid {
        journals[journal.id] = journal
        journalsFlow.value = journals.values.toList()
        return journal.id
    }

    override suspend fun update(journal: Journal) {
        journals[journal.id] = journal
        journalsFlow.value = journals.values.toList()
    }

    override suspend fun delete(journalId: Uuid) {
        journals.remove(journalId)
        journalsFlow.value = journals.values.toList()
    }

    override suspend fun saveDraft(draft: EditorDraft) {
        drafts.removeAll { it.id == draft.id }
        drafts.add(draft)
    }

    override suspend fun getLatestDraft(): EditorDraft? = drafts.maxByOrNull { it.lastModifiedAt }

    override suspend fun getAllDrafts(): List<EditorDraft> = drafts.toList()

    override suspend fun getDraft(id: Uuid): EditorDraft? = drafts.find { it.id == id }

    override suspend fun deleteDraft(id: Uuid) {
        drafts.removeAll { it.id == id }
    }
}

/**
 * In-memory notes repository that supports both export-side reads and import-side writes.
 */
internal class RoundTripJournalNotesRepository : JournalNotesRepository {
    private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())
    private val notes = mutableMapOf<Uuid, JournalNote>()
    var notesByJournal: Map<Uuid, List<JournalNote>> = emptyMap()

    var testNotes: List<JournalNote> = emptyList()
        set(value) {
            field = value
            value.forEach { notes[it.uid] = it }
            notesFlow.value = value
        }

    override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(notesByJournal[journalId] ?: emptyList())

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes[noteId]

    override suspend fun create(note: JournalNote): Uuid {
        notes[note.uid] = note
        notesFlow.value = notes.values.toList()
        return note.uid
    }

    override suspend fun remove(note: JournalNote) {
        notes.remove(note.uid)
        notesFlow.value = notes.values.toList()
    }

    override suspend fun removeById(noteId: Uuid) {
        notes.remove(noteId)
        notesFlow.value = notes.values.toList()
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        notes[note.uid] = note
        notesFlow.value = notes.values.toList()
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {}

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> =
        notesByJournal.flatMap { (journalId, notes) ->
            notes.map { note -> journalId to note.uid }
        }
}

/**
 * In-memory content repository that tracks journal-note links.
 */
internal class RoundTripJournalContentRepository : JournalContentRepository {
    private val links = mutableListOf<Pair<Uuid, Uuid>>()

    fun allLinks(): List<Pair<Uuid, Uuid>> = links.toList()

    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

    override suspend fun addContentToJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        links.add(contentId to journalId)
    }

    override suspend fun removeContentFromJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        links.removeAll { it.first == contentId && it.second == journalId }
    }

    override suspend fun addContentToJournals(
        contentId: Uuid,
        journalIds: List<Uuid>,
    ) {
        journalIds.forEach { links.add(contentId to it) }
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) {
        links.removeAll { it.first == contentId }
    }

    override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())
}

internal class StubDeviceIdProvider(
    initialId: Uuid,
) : DeviceIdProvider {
    private val deviceId = MutableStateFlow(initialId)

    override fun getDeviceId(): MutableStateFlow<Uuid> = deviceId

    override suspend fun refreshDeviceId() {}
}

internal class StubAppInfoProvider(
    private val appInfo: AppInfo,
) : AppInfoProvider {
    override fun getAppInfo(): AppInfo = appInfo
}

internal class StubUserStateRepository : UserStateRepository {
    override val userData: Flow<UserData> = flowOf(UserData())

    override suspend fun setBirthday(birthday: Instant) {}

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

    override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

    override suspend fun addFavoriteNote(vararg noteId: String) {}
}

internal class RoundTripProfileRepository : ProfileRepository {
    var profile: LogDateProfile = LogDateProfile()

    override val currentProfile: Flow<LogDateProfile> = flowOf(profile)

    override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> {
        profile = profile.copy(displayName = displayName)
        return Result.success(profile)
    }

    override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> {
        profile = profile.copy(birthday = birthday)
        return Result.success(profile)
    }

    override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> {
        profile = profile.copy(profilePhotoUri = profilePhotoUri)
        return Result.success(profile)
    }

    override suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<LogDateProfile> {
        profile = profile.copy(bio = bio, originalBio = originalBio)
        return Result.success(profile)
    }

    override suspend fun getCurrentProfile(): LogDateProfile = profile

    override suspend fun clearProfile(): Result<Unit> {
        profile = LogDateProfile()
        return Result.success(Unit)
    }
}

internal class RoundTripUserPlacesRepository : UserPlacesRepository {
    var places: List<Place> = emptyList()

    override suspend fun getAllPlaces(): List<Place> = places

    override fun observeAllPlaces(): Flow<List<Place>> = flowOf(places)

    override suspend fun getPlacesNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): List<Place> = places

    override suspend fun getPlaceById(placeId: String): Place? = places.find { it.uid.toString() == placeId }

    override suspend fun createPlace(place: Place): Result<Place> {
        places = places + place
        return Result.success(place)
    }

    override suspend fun updatePlace(place: Place): Result<Place> {
        places = places.filterNot { it.uid == place.uid } + place
        return Result.success(place)
    }

    override suspend fun deletePlace(placeId: String): Result<Unit> {
        places = places.filterNot { it.uid.toString() == placeId }
        return Result.success(Unit)
    }

    override suspend fun searchPlaces(query: String): List<Place> = places.filter { it.name.contains(query, ignoreCase = true) }
}

internal class RoundTripLocationHistoryRepository : LocationHistoryRepository {
    var entries: List<LocationHistoryItem> = emptyList()

    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = entries

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(entries)

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = entries.take(limit)

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> = entries.filter { it.timestamp in startTime..endTime }

    override suspend fun getLastLocation(): LocationHistoryItem? = entries.maxByOrNull { it.timestamp }

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(entries.maxByOrNull { it.timestamp })

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun logLocation(record: LocationLogRecord): Result<Unit> {
        entries =
            entries +
            LocationHistoryItem(
                sampleId = record.sampleId,
                userId = record.userId,
                deviceId = record.deviceId,
                timestamp = record.timestamp,
                loggedAt = record.loggedAt,
                location = record.location,
                confidence = record.confidence,
                isGenuine = record.isGenuine,
                capturePipeline = record.capturePipeline,
                captureSource = record.captureSource,
                accuracyMeters = record.accuracyMeters,
                speedMetersPerSecond = record.speedMetersPerSecond,
                bearingDegrees = record.bearingDegrees,
                isMock = record.isMock,
            )
        return Result.success(Unit)
    }

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getLocationCount(): Int = entries.size
}
