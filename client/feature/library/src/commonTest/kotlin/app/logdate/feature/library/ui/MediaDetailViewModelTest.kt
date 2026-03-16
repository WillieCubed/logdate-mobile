package app.logdate.feature.library.ui

import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.location.places.GeocodedAddress
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.client.location.places.ReverseGeocodingProvider
import app.logdate.client.media.display.StubRemoteDisplayManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
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
    fun missingNoteProducesError() =
        runTest(testDispatcher) {
            val repository = FakeJournalNotesRepository(emptyList())
            val noteId = Uuid.random()
            val viewModel =
                MediaDetailViewModel(
                    noteId,
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
    fun imageNoteProducesImageContent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Image(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/images/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    noteId,
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
            assertEquals(noteId, state.noteId)
            collectJob.cancel()
        }

    @Test
    fun videoNoteProducesVideoContent() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Video(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        mediaRef = "content://media/external/video/1",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    noteId,
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
            val noteId = Uuid.random()
            val location =
                NoteLocation(
                    coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
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
                    noteId,
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
    fun textNoteProducesError() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val notes =
                listOf(
                    JournalNote.Text(
                        uid = noteId,
                        creationTimestamp = Instant.fromEpochMilliseconds(1710000000000),
                        lastUpdated = Instant.fromEpochMilliseconds(1710000000000),
                        content = "Not a photo",
                    ),
                )
            val repository = FakeJournalNotesRepository(notes)
            val viewModel =
                MediaDetailViewModel(
                    noteId,
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
    fun crossReferencesPopulatedFromJournals() =
        runTest(testDispatcher) {
            val noteId = Uuid.random()
            val journalId = Uuid.random()
            val now = Instant.fromEpochMilliseconds(1710000000000)

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
                    noteId,
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
}
