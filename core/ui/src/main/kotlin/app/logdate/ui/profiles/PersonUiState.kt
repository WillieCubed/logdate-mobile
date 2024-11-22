package app.logdate.ui.profiles

import app.logdate.model.Person
import kotlin.uuid.ExperimentalUuidApi

data class PersonUiState(
    val personId: String,
    val name: String,
    val photoUri: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
fun Person.toUiState(): PersonUiState = PersonUiState(
    personId = this.uid.toString(),
    name = this.name
)