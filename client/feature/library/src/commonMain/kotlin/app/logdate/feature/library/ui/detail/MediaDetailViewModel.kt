package app.logdate.feature.library.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * ViewModel for the media detail screen.
 *
 * Loads a single image or video note by its ID and exposes its content, metadata,
 * the journals it appears in, camera EXIF data, and resolved location names.
 */
class MediaDetailViewModel(
    private val noteId: Uuid,
    private val notesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val indexedMediaRepository: IndexedMediaRepository,
    private val resolveLocationToPlaceUseCase: ResolveLocationToPlaceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MediaDetailUiState>(MediaDetailUiState.Loading)
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    init {
        observeNote()
    }

    private fun observeNote() {
        viewModelScope.launch {
            notesRepository.allNotesObserved
                .catch { error ->
                    Napier.e("Failed to load media detail", error)
                    _uiState.value = MediaDetailUiState.Error("Could not load this photo or video.")
                }.collect { notes ->
                    val note = notes.find { it.uid == noteId }
                    if (note == null) {
                        _uiState.value = MediaDetailUiState.Error("Media not found.")
                    } else {
                        updateForNote(note)
                    }
                }
        }
    }

    private suspend fun updateForNote(note: JournalNote) =
        coroutineScope {
            val journalsDeferred =
                async {
                    try {
                        journalContentRepository
                            .observeJournalsForContent(note.uid)
                            .first()
                            .map { JournalReference(id = it.id, title = it.title) }
                    } catch (e: Exception) {
                        Napier.e("Failed to load cross-references", e)
                        emptyList()
                    }
                }

            val exifDeferred =
                async {
                    if (note is JournalNote.Image) {
                        try {
                            indexedMediaRepository.getExifMetadata(note.uid)?.let { metadata ->
                                ExifDisplayData(
                                    cameraMake = metadata.cameraMake,
                                    cameraModel = metadata.cameraModel,
                                    aperture = metadata.aperture,
                                    iso = metadata.iso,
                                    focalLength = metadata.focalLength,
                                    shutterSpeed = metadata.shutterSpeed,
                                )
                            }
                        } catch (e: Exception) {
                            Napier.e("Failed to load EXIF data", e)
                            null
                        }
                    } else {
                        null
                    }
                }

            val locationDeferred = async { resolveLocationName(note.location) }

            val journals = journalsDeferred.await()
            val exif = exifDeferred.await()
            val locationDisplayName = locationDeferred.await()

            _uiState.value =
                when (note) {
                    is JournalNote.Image ->
                        MediaDetailUiState.ImageContent(
                            noteId = note.uid,
                            mediaRef = note.mediaRef,
                            createdAt = note.creationTimestamp,
                            location = note.location,
                            locationDisplayName = locationDisplayName,
                            journals = journals,
                            exif = exif,
                        )
                    is JournalNote.Video ->
                        MediaDetailUiState.VideoContent(
                            noteId = note.uid,
                            mediaRef = note.mediaRef,
                            createdAt = note.creationTimestamp,
                            location = note.location,
                            locationDisplayName = locationDisplayName,
                            journals = journals,
                        )
                    else -> MediaDetailUiState.Error("Not a photo or video.")
                }
        }

    /**
     * Resolves the best available display name for a note's location.
     *
     * Fallback chain: resolved place name → note's display name → raw coordinates → null.
     */
    private suspend fun resolveLocationName(noteLocation: NoteLocation?): String? {
        if (noteLocation == null) return null

        val coords = noteLocation.coordinates
        if (coords != null) {
            try {
                val location =
                    Location(
                        coords.latitude,
                        coords.longitude,
                        altitude = LocationAltitude(coords.altitude ?: 0.0, AltitudeUnit.METERS),
                    )
                val resolved =
                    when (val result = resolveLocationToPlaceUseCase(location)) {
                        is PlaceResolutionResult.UserDefinedPlace -> result.place.name
                        is PlaceResolutionResult.ExternalSuggestion -> result.suggestion.name
                        is PlaceResolutionResult.CoarseLocation -> {
                            val addr = result.address
                            listOfNotNull(addr.locality, addr.adminArea)
                                .joinToString(", ")
                                .ifEmpty { null }
                        }
                        is PlaceResolutionResult.UnknownLocation -> null
                    }
                if (resolved != null) return resolved
            } catch (e: Exception) {
                Napier.w("Failed to resolve location name", e)
            }
        }

        return noteLocation.displayName
            ?: coords?.let { "${it.latitude}, ${it.longitude}" }
    }
}
