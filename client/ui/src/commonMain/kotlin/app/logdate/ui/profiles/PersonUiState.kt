package app.logdate.ui.profiles

import app.logdate.shared.model.Person
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class PersonUiState(
    /**
     * An identifier for this person that remains constant even across this person's name changes.
     */
    val uid: Uuid,
    // TODO: Account for aliases and other types of relationships ("e.g. 'Mom', "The guy from the coffee shop")
    val name: String,
    /**
     * A URI to an image of this person.
     */
    val photoUri: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
fun Person.toUiState(): PersonUiState = PersonUiState(
    uid = this.uid,
    name = this.name
)