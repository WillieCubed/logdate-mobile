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
    /**
     * Extracts people's names from the given text.
     *
     * @param documentId An ID used to identify and cache the response.
     * @param text The text to extract people's names from.
     */
    suspend operator fun invoke(documentId: String, text: String): List<Person> {
        return peopleExtractor.extractPeople(documentId, text)
    }
}