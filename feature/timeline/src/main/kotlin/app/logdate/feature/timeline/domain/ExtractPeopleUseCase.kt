package app.logdate.feature.timeline.domain

import app.logdate.core.intelligence.entity.people.PeopleExtractor
import app.logdate.model.Person
import jakarta.inject.Inject

/**
 * A use case to extract people's names from text.
 *
 * TODO: Use internal database for more intelligent name resolution
 */
class ExtractPeopleUseCase @Inject constructor(
    private val peopleExtractor: PeopleExtractor,
) {
    suspend operator fun invoke(text: String): List<Person> {
        return peopleExtractor.extractPeople(text)
    }
}