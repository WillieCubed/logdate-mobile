package app.logdate.client.domain.restore

import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.Place
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal object RestoreArchiveTestFixtures {
    class FakeJournalRepository : JournalRepository {
        val existingJournals = mutableMapOf<Uuid, Journal>()
        val created = mutableListOf<Journal>()
        val updated = mutableListOf<Journal>()
        val deleted = mutableListOf<Uuid>()
        val savedDrafts = mutableListOf<EditorDraft>()
        val deletedDrafts = mutableListOf<Uuid>()

        override val allJournalsObserved: Flow<List<Journal>> = flowOf(emptyList())

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(existingJournals[id] ?: Journal(id = id))

        override suspend fun getJournalById(id: Uuid): Journal? = existingJournals[id]

        var batchReads = 0

        override suspend fun getJournalsByIds(ids: Set<Uuid>): Map<Uuid, Journal> {
            batchReads++
            return existingJournals.filterKeys { it in ids }
        }

        override suspend fun create(journal: Journal): Uuid {
            created.add(journal)
            existingJournals[journal.id] = journal
            return journal.id
        }

        override suspend fun update(journal: Journal) {
            updated.add(journal)
            existingJournals[journal.id] = journal
        }

        override suspend fun delete(journalId: Uuid) {
            deleted.add(journalId)
            existingJournals.remove(journalId)
        }

        override suspend fun saveDraft(draft: EditorDraft) {
            savedDrafts.removeAll { it.id == draft.id }
            savedDrafts.add(draft)
        }

        override suspend fun getLatestDraft(): EditorDraft? = null

        override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = savedDrafts.lastOrNull { it.id == id }

        override suspend fun deleteDraft(id: Uuid) {
            deletedDrafts.add(id)
            savedDrafts.removeAll { it.id == id }
        }
    }

    class FakeJournalNotesRepository : JournalNotesRepository {
        val existingNotes = mutableMapOf<Uuid, JournalNote>()
        val created = mutableListOf<JournalNote>()
        val removed = mutableListOf<Uuid>()
        var throwAfterCreates: Int = Int.MAX_VALUE

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

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

        var singleReads = 0
        var batchReads = 0

        override suspend fun getNoteById(noteId: Uuid): JournalNote? {
            singleReads++
            return existingNotes[noteId]
        }

        override suspend fun getNotesByIds(noteIds: Set<Uuid>): Map<Uuid, JournalNote> {
            batchReads++
            return existingNotes.filterKeys { it in noteIds }
        }

        override suspend fun create(note: JournalNote): Uuid {
            if (created.size >= throwAfterCreates) {
                throw RuntimeException("Simulated failure after ${created.size} creates")
            }
            created.add(note)
            existingNotes[note.uid] = note
            return note.uid
        }

        override suspend fun compareAndSetNoteForRestore(
            expected: JournalNote?,
            replacement: JournalNote,
            scope: app.logdate.client.repository.journals.JournalMergeScope?,
            recoveryId: String?,
        ): Boolean {
            if (existingNotes[replacement.uid] != expected) return false
            create(replacement)
            return true
        }

        override suspend fun remove(note: JournalNote) {
            removed.add(note.uid)
            existingNotes.remove(note.uid)
        }

        override suspend fun removeById(noteId: Uuid) {
            removed.add(noteId)
            existingNotes.remove(noteId)
        }

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {
            if (created.size >= throwAfterCreates) {
                throw RuntimeException("Simulated failure after ${created.size} creates")
            }
            created.add(note)
            existingNotes[note.uid] = note
        }

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    /** Distinguishes the queueing write from the from-sync one, which the plain fake cannot. */
    class FakeSyncableJournalContentRepository : SyncableJournalContentRepository {
        val queuedLinks = mutableListOf<Pair<Uuid, Uuid>>()
        val unqueuedLinks = mutableListOf<Pair<Uuid, Uuid>>()

        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            queuedLinks.add(contentId to journalId)
        }

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) {
            journalIds.forEach { queuedLinks.add(contentId to it) }
        }

        override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit

        override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())

        override suspend fun addContentToJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            unqueuedLinks.add(contentId to journalId)
        }

        override suspend fun removeContentFromJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit
    }

    class FakeJournalContentRepository : JournalContentRepository {
        val links = mutableListOf<Pair<Uuid, Uuid>>()
        val removedLinks = mutableListOf<Pair<Uuid, Uuid>>()
        var linkFailure: Throwable? = null

        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            linkFailure?.let { throw it }
            links.add(contentId to journalId)
        }

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            removedLinks.add(contentId to journalId)
            links.removeAll { it.first == contentId && it.second == journalId }
        }

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) {
            journalIds.forEach { links.add(contentId to it) }
        }

        override suspend fun removeContentFromAllJournals(contentId: Uuid) {}

        override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())
    }

    class FakeMediaImporter(
        private val mappings: Map<String, String> = emptyMap(),
    ) : MediaImporter {
        override suspend fun importMedia(exportPath: String): String? = mappings[exportPath]
    }

    class FakeProfileRepository : ProfileRepository {
        var profile: LogDateProfile = LogDateProfile()
        var updateDisplayNameCallCount = 0
        var updateBirthdayCallCount = 0
        var updateProfilePhotoCallCount = 0
        var updateBioCallCount = 0

        override val currentProfile: Flow<LogDateProfile> = flowOf(profile)

        override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> {
            updateDisplayNameCallCount++
            profile = profile.copy(displayName = displayName)
            return Result.success(profile)
        }

        override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> {
            updateBirthdayCallCount++
            profile = profile.copy(birthday = birthday)
            return Result.success(profile)
        }

        override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> {
            updateProfilePhotoCallCount++
            profile = profile.copy(profilePhotoUri = profilePhotoUri)
            return Result.success(profile)
        }

        override suspend fun updateBio(
            bio: String?,
            originalBio: String?,
        ): Result<LogDateProfile> {
            updateBioCallCount++
            profile = profile.copy(bio = bio, originalBio = originalBio)
            return Result.success(profile)
        }

        override suspend fun getCurrentProfile(): LogDateProfile = profile

        override suspend fun clearProfile(): Result<Unit> {
            profile = LogDateProfile()
            return Result.success(Unit)
        }
    }

    class FakeUserPlacesRepository : UserPlacesRepository {
        var places = emptyList<Place>()

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

    class FakeLocationHistoryRepository : LocationHistoryRepository {
        var entries = emptyList<LocationHistoryItem>()
        var getAllLocationHistoryCallCount = 0
        var logLocationCallCount = 0
        var throwOnGetAllLocationHistory = false

        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> {
            getAllLocationHistoryCallCount++
            if (throwOnGetAllLocationHistory) {
                error("getAllLocationHistory should not be called during restore")
            }
            return entries
        }

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
            logLocationCallCount++
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

    // endregion
}
