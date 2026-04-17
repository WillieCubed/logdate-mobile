package app.logdate.client.repository.knowledge

import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Event
import app.logdate.shared.model.Person
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

data class PersonRelatedContent(
    val person: Person,
    val linkedEntries: List<JournalNote>,
    val linkedEvents: List<Event>,
)

interface PeopleProfileRepository {
    fun observePerson(personId: Uuid): Flow<Person?>

    fun observeProfile(personId: Uuid): Flow<PersonRelatedContent?>
}
