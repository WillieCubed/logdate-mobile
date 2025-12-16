package app.logdate.client.domain.entities

import app.logdate.client.repository.knowledge.PeopleRepository
import app.logdate.shared.model.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.uuid.Uuid

class GetPeopleUseCaseTest {

    private class MockPeopleRepository : PeopleRepository {
        var getAllPeopleResult: Flow<List<Person>> = flowOf(emptyList())

        override fun getAllPeople(): Flow<List<Person>> = getAllPeopleResult

        override suspend fun getPerson(uid: Uuid): Person = Person(name = "Test")
        override suspend fun resolvePersonByName(name: String): Person? = null
        override suspend fun resolvePersonByDescription(description: String): Person? = null
        override suspend fun addPerson(person: Person) = Unit
        override suspend fun updatePerson(person: Person) = Unit
        override suspend fun deletePerson(uid: Uuid) = Unit
        override suspend fun addAliasToPerson(personUid: Uuid, alias: String) = Unit
        override suspend fun removeAliasFromPerson(personUid: Uuid, alias: String) = Unit
    }

    @Test
    fun `invoke should return flow of all people from repository`() = runTest {
        // Given
        val mockRepository = MockPeopleRepository()
        val expectedPeople = listOf(
            Person(name = "John Smith"),
            Person(name = "Jane Doe"),
            Person(name = "Bob Wilson")
        )
        mockRepository.getAllPeopleResult = flowOf(expectedPeople)
        val useCase = GetPeopleUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(expectedPeople, emittedValues.first())
    }

    @Test
    fun `invoke should return empty flow when no people exist`() = runTest {
        // Given
        val mockRepository = MockPeopleRepository()
        mockRepository.getAllPeopleResult = flowOf(emptyList())
        val useCase = GetPeopleUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(emptyList(), emittedValues.first())
    }

    @Test
    fun `invoke should return single person when only one exists`() = runTest {
        // Given
        val mockRepository = MockPeopleRepository()
        val singlePerson = Person(name = "Alice Cooper")
        mockRepository.getAllPeopleResult = flowOf(listOf(singlePerson))
        val useCase = GetPeopleUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(listOf(singlePerson), emittedValues.first())
    }

    @Test
    fun `invoke should handle multiple emissions from repository`() = runTest {
        // Given
        val mockRepository = MockPeopleRepository()
        val firstEmission = listOf(Person(name = "John"))
        val secondEmission = listOf(
            Person(name = "John"),
            Person(name = "Jane")
        )
        mockRepository.getAllPeopleResult = flowOf(firstEmission, secondEmission)
        val useCase = GetPeopleUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(2, emittedValues.size)
        assertEquals(firstEmission, emittedValues[0])
        assertEquals(secondEmission, emittedValues[1])
    }

    @Test
    fun `invoke should preserve order of people from repository`() = runTest {
        // Given
        val mockRepository = MockPeopleRepository()
        val orderedPeople = listOf(
            Person(name = "Charlie"),
            Person(name = "Alice"),
            Person(name = "Bob")
        )
        mockRepository.getAllPeopleResult = flowOf(orderedPeople)
        val useCase = GetPeopleUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(orderedPeople, emittedValues.first())
        assertEquals("Charlie", emittedValues.first()[0].name)
        assertEquals("Alice", emittedValues.first()[1].name)
        assertEquals("Bob", emittedValues.first()[2].name)
    }
}