package app.logdate.feature.library.ui

import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.location.places.GeocodedAddress
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.client.location.places.ReverseGeocodingProvider
import app.logdate.client.media.display.StubRemoteDisplayManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.media.ExifMetadata
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.feature.library.fakes.FakeIndexedMediaRepository
import app.logdate.feature.library.fakes.FakeJournalContentRepository
import app.logdate.feature.library.fakes.FakeJournalNotesRepository
import app.logdate.feature.library.ui.detail.MediaDetailUiState
import app.logdate.feature.library.ui.detail.MediaDetailViewModel
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val contentRepository = FakeJournalContentRepository()
    private val indexedMediaRepository = FakeIndexedMediaRepository()
    private val remoteDisplayManager = StubRemoteDisplayManager()
    private val resolveLocationUseCase =
        ResolveLocationToPlaceUseCase(
            userPlacesRepository =
                object : UserPlacesRepository {
                    override suspend fun getAllPlaces(): List<Place> = emptyList()

                    override fun observeAllPlaces() = kotlinx.coroutines.flow.flowOf(emptyList<Place>())

                    override suspend fun getPlacesNear(
                        latitude: Double,
                        longitude: Double,
                        radiusMeters: Double,
                    ): List<Place> = emptyList()

                    override suspend fun getPlaceById(placeId: String): Place? = null

                    override suspend fun createPlace(place: Place): Result<Place> = Result.success(place)

                    override suspend fun updatePlace(place: Place): Result<Place> = Result.success(place)

                    override suspend fun deletePlace(placeId: String): Result<Unit> = Result.success(Unit)

                    override suspend fun searchPlaces(query: String): List<Place> = emptyList()
                },
            externalPlacesProvider =
                object : app.logdate.client.location.places.ExternalPlacesProvider {
                    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = emptyList()
                },
            reverseGeocodingProvider =
                object : ReverseGeocodingProvider {
                    override suspend fun reverseGeocode(location: Location): GeocodedAddress? = null
                },
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun missingMediaProducesError() =
        runTest(testDispatcher) {
            val repository = FakeJournalNotesRepository(emptyList())
            val mediaId = Uuid.random()
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<MediaDetailUiState.Error>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun indexedImageProducesImageContentWithoutMatchingNote() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                ),
            )
            val repository = FakeJournalNotesRepository(emptyList())
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals("content://media/external/images/1", state.mediaRef)
            assertEquals(mediaId, state.mediaId)
            collectJob.cancel()
        }

    @Test
    fun noteOnlyImageStillProducesImageContent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/note-only",
                    ),
                )

            val viewModel =
                MediaDetailViewModel(
                    noteId,
                    FakeJournalNotesRepository(notes),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(noteId, state.mediaId)
            assertEquals("content://media/external/images/note-only", state.mediaRef)
            collectJob.cancel()
        }

    @Test
    fun indexedImageUsesIndexedExifMetadata() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/2",
                        timestamp = Instant.fromEpochMilliseconds(1710200000000),
                    ),
                ),
            )
            indexedMediaRepository.setExifMetadata(
                mediaId,
                ExifMetadata(
                    cameraMake = "Fujifilm",
                    cameraModel = "X100V",
                    iso = 400,
                ),
            )

            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    FakeJournalNotesRepository(emptyList()),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            val exif = assertNotNull(state.exif)
            assertEquals("Fujifilm", exif.cameraMake)
            assertEquals("X100V", exif.cameraModel)
            assertEquals(400, exif.iso)
            collectJob.cancel()
        }

    @Test
    fun indexedVideoProducesVideoContentWithoutMatchingNote() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Video(
                        uid = mediaId,
                        uri = "content://media/external/video/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                        duration = 8.seconds,
                    ),
                ),
            )
            val repository = FakeJournalNotesRepository(emptyList())
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.VideoContent>(state)
            assertEquals("content://media/external/video/1", state.mediaRef)
            collectJob.cancel()
        }

    @Test
    fun locationDataIncludedWhenPresent() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            val noteId = Uuid.random()
            val location =
                NoteLocation(
                    coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                )
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                ),
            )
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                        location = location,
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(37.7749, state.location?.coordinates?.latitude)
            collectJob.cancel()
        }

    @Test
    fun textOnlyNotesDoNotBlockIndexedMedia() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/1",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                ),
            )
            val notes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        content = "Not a photo",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertIs<MediaDetailUiState.ImageContent>(viewModel.uiState.value)
            collectJob.cancel()
        }

    @Test
    fun crossReferencesPopulatedFromJournals() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            val noteId = Uuid.random()
            val journalId = Uuid.random()
            val now = Instant.fromEpochMilliseconds(1710000000000)
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/1",
                        timestamp = now,
                    ),
                ),
            )

            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = now,
                        lastUpdated = now,
                        mediaRef = "content://media/external/images/1",
                    ),
                )

            contentRepository.setJournalsForContent(
                noteId,
                listOf(
                    Journal(
                        id = journalId,
                        title = "Trip to Paris",
                        created = now,
                        lastUpdated = now,
                    ),
                ),
            )

            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    repository,
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(1, state.journals.size)
            assertEquals("Trip to Paris", state.journals[0].title)
            assertEquals(journalId, state.journals[0].id)
            collectJob.cancel()
        }

    @Test
    fun matchingNotesUseNewestLocationAndDeduplicateJournals() =
        runTest(testDispatcher) {
            val mediaId = Uuid.random()
            val olderNoteId = Uuid.random()
            val newerNoteId = Uuid.random()
            val sharedJournalId = Uuid.random()
            val secondJournalId = Uuid.random()
            val olderTime = Instant.fromEpochMilliseconds(1710000000000)
            val newerTime = Instant.fromEpochMilliseconds(1710100000000)
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = mediaId,
                        uri = "content://media/external/images/shared",
                        timestamp = newerTime,
                    ),
                ),
            )

            val notes =
                listOf(
                    JournalNote.Image(
                        uid = olderNoteId,
                        creationTimestamp = olderTime,
                        lastUpdated = olderTime,
                        mediaRef = "content://media/external/images/shared",
                        location =
                            NoteLocation(
                                coordinates = NoteCoordinates(latitude = 10.0, longitude = 10.0),
                            ),
                    ),
                    JournalNote.Image(
                        uid = newerNoteId,
                        creationTimestamp = newerTime,
                        lastUpdated = newerTime,
                        mediaRef = "content://media/external/images/shared",
                        location =
                            NoteLocation(
                                coordinates = NoteCoordinates(latitude = 20.0, longitude = 20.0),
                            ),
                    ),
                )

            contentRepository.setJournalsForContent(
                olderNoteId,
                listOf(
                    Journal(
                        id = sharedJournalId,
                        title = "Shared journal",
                        created = olderTime,
                        lastUpdated = olderTime,
                    ),
                ),
            )
            contentRepository.setJournalsForContent(
                newerNoteId,
                listOf(
                    Journal(
                        id = sharedJournalId,
                        title = "Shared journal",
                        created = newerTime,
                        lastUpdated = newerTime,
                    ),
                    Journal(
                        id = secondJournalId,
                        title = "Second journal",
                        created = newerTime,
                        lastUpdated = newerTime,
                    ),
                ),
            )

            val viewModel =
                MediaDetailViewModel(
                    mediaId,
                    FakeJournalNotesRepository(notes),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(20.0, state.location?.coordinates?.latitude)
            assertEquals(2, state.journals.size)
            assertEquals(listOf("Shared journal", "Second journal"), state.journals.map { it.title })
            collectJob.cancel()
        }

    @Test
    fun presenterStateUsesVisibleLibraryMediaList() =
        runTest(testDispatcher) {
            val olderMediaId = Uuid.random()
            val currentMediaId = Uuid.random()
            val newerMediaId = Uuid.random()
            val noteOnlyId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = olderMediaId,
                        uri = "content://media/external/images/old",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                    IndexedMedia.Image(
                        uid = currentMediaId,
                        uri = "content://media/external/images/current",
                        timestamp = Instant.fromEpochMilliseconds(1710100000000),
                    ),
                    IndexedMedia.Video(
                        uid = newerMediaId,
                        uri = "content://media/external/video/new",
                        timestamp = Instant.fromEpochMilliseconds(1710200000000),
                        duration = 12.seconds,
                    ),
                ),
            )
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteOnlyId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710050000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710050000000),
                        mediaRef = "content://media/external/images/note-only-presenter",
                    ),
                )

            val viewModel =
                MediaDetailViewModel(
                    currentMediaId,
                    FakeJournalNotesRepository(notes),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val stateJob = launch { viewModel.uiState.collect {} }
            val presenterJob = launch { viewModel.presenterState.collect {} }
            advanceUntilIdle()

            val presenterState = viewModel.presenterState.value
            assertEquals(4, presenterState.totalItems)
            assertEquals(1, presenterState.currentIndex)
            assertEquals(
                listOf(newerMediaId, currentMediaId, noteOnlyId, olderMediaId),
                presenterState.mediaItems.map { it.uid },
            )
            assertEquals(true, presenterState.mediaItems.first().isVideo)
            presenterJob.cancel()
            stateJob.cancel()
        }

    @Test
    fun viewerStateUsesVisibleLibraryMediaList() =
        runTest(testDispatcher) {
            val currentMediaId = Uuid.random()
            val newestMediaId = Uuid.random()
            val noteOnlyId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = currentMediaId,
                        uri = "content://media/external/images/current",
                        timestamp = Instant.fromEpochMilliseconds(1710100000000),
                    ),
                    IndexedMedia.Image(
                        uid = newestMediaId,
                        uri = "content://media/external/images/newest",
                        timestamp = Instant.fromEpochMilliseconds(1710200000000),
                    ),
                ),
            )
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteOnlyId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710150000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710150000000),
                        mediaRef = "content://media/external/images/note-only",
                    ),
                )
            val viewModel =
                MediaDetailViewModel(
                    currentMediaId,
                    FakeJournalNotesRepository(notes),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val stateJob = launch { viewModel.uiState.collect {} }
            val viewerJob = launch { viewModel.viewerState.collect {} }
            advanceUntilIdle()

            val viewerState = viewModel.viewerState.value
            assertEquals(3, viewerState.totalItems)
            assertEquals(2, viewerState.currentIndex)
            assertEquals(
                listOf(newestMediaId, noteOnlyId, currentMediaId),
                viewerState.mediaItems.map { it.uid },
            )
            viewerJob.cancel()
            stateJob.cancel()
        }

    @Test
    fun selectMediaUpdatesCurrentContentAndIndexes() =
        runTest(testDispatcher) {
            val firstMediaId = Uuid.random()
            val secondMediaId = Uuid.random()
            indexedMediaRepository.setMedia(
                listOf(
                    IndexedMedia.Image(
                        uid = firstMediaId,
                        uri = "content://media/external/images/first",
                        timestamp = Instant.fromEpochMilliseconds(1710000000000),
                    ),
                    IndexedMedia.Image(
                        uid = secondMediaId,
                        uri = "content://media/external/images/second",
                        timestamp = Instant.fromEpochMilliseconds(1710100000000),
                    ),
                ),
            )
            val viewModel =
                MediaDetailViewModel(
                    firstMediaId,
                    FakeJournalNotesRepository(emptyList()),
                    contentRepository,
                    indexedMediaRepository,
                    resolveLocationUseCase,
                    remoteDisplayManager,
                )

            val stateJob = launch { viewModel.uiState.collect {} }
            val viewerJob = launch { viewModel.viewerState.collect {} }
            val presenterJob = launch { viewModel.presenterState.collect {} }
            advanceUntilIdle()

            viewModel.selectMedia(0)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<MediaDetailUiState.ImageContent>(state)
            assertEquals(secondMediaId, state.mediaId)
            assertEquals("content://media/external/images/second", state.mediaRef)
            assertEquals(0, viewModel.viewerState.value.currentIndex)
            assertEquals(0, viewModel.presenterState.value.currentIndex)
            presenterJob.cancel()
            viewerJob.cancel()
            stateJob.cancel()
        }
}
