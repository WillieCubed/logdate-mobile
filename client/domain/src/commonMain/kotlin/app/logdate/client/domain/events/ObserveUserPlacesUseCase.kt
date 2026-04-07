package app.logdate.client.domain.events

import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the user's saved places, narrowed to [Place.UserDefined] for surfaces that only
 * deal with user-defined places (e.g. the event place picker).
 *
 * Wrapping [UserPlacesRepository.observeAllPlaces] in a use case keeps ViewModels off the
 * repository directly, per the project's clean-architecture rules.
 */
class ObserveUserPlacesUseCase(
    private val repository: UserPlacesRepository,
) {
    operator fun invoke(): Flow<List<Place.UserDefined>> =
        repository.observeAllPlaces().map { places -> places.filterIsInstance<Place.UserDefined>() }
}
