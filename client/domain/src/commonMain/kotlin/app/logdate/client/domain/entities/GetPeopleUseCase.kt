package app.logdate.client.domain.entities

import app.logdate.client.repository.knowledge.PeopleRepository

/**
 * A use case to get all people known to the client.
 */
class GetPeopleUseCase(
    private val peopleRepository: PeopleRepository,
) {
    operator fun invoke() = peopleRepository.getAllPeople()
}