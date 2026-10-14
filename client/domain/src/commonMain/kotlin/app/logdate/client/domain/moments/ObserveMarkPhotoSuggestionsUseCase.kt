package app.logdate.client.domain.moments

import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.PhotoLibraryAccess
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

sealed interface MarkPhotoSuggestions {
    data object Loading : MarkPhotoSuggestions

    data object NeedsAccess : MarkPhotoSuggestions

    data object None : MarkPhotoSuggestions

    data class Limited(
        val photos: List<MediaObject.Image>,
    ) : MarkPhotoSuggestions

    data class Photos(
        val photos: List<MediaObject.Image>,
    ) : MarkPhotoSuggestions
}

class ObserveMarkPhotoSuggestionsUseCase(
    private val media: MediaManager,
) {
    operator fun invoke(mark: MarkedMoment): Flow<MarkPhotoSuggestions> =
        flow {
            emit(MarkPhotoSuggestions.Loading)
            val access = media.photoLibraryAccess()
            if (access == PhotoLibraryAccess.DENIED) {
                emit(MarkPhotoSuggestions.NeedsAccess)
                return@flow
            }
            try {
                emitAll(
                    media.observeMediaByDate(mark.capturedAt - 15.minutes, mark.capturedAt + 15.minutes + 1.milliseconds).map { all ->
                        val photos = selectMarkPhotos(mark.capturedAt, all)
                        when {
                            media.photoLibraryAccess() == PhotoLibraryAccess.LIMITED -> MarkPhotoSuggestions.Limited(photos)
                            photos.isEmpty() -> MarkPhotoSuggestions.None
                            else -> MarkPhotoSuggestions.Photos(photos)
                        }
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                io.github.aakira.napier.Napier
                    .w("Could not read photos around marked moment", error)
                emit(MarkPhotoSuggestions.NeedsAccess)
            }
        }
}
