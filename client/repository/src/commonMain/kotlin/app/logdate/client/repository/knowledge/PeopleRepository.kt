package app.logdate.client.repository.knowledge

import app.logdate.shared.model.Person
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface PeopleRepository {

    suspend fun getPerson(uid: Uuid): Person

    fun getAllPeople(): Flow<List<Person>>

    suspend fun resolvePersonByName(
        name: String,
    ): Person?

    suspend fun resolvePersonByDescription(
        description: String,
    ): Person?

    suspend fun addPerson(person: Person)

    suspend fun updatePerson(person: Person)

    suspend fun deletePerson(uid: Uuid)

    suspend fun addAliasToPerson(
        personUid: Uuid,
        alias: String,
    )

    suspend fun removeAliasFromPerson(
        personUid: Uuid,
        alias: String,
    )
}