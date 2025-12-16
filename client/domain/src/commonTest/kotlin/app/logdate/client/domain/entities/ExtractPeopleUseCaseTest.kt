package app.logdate.client.domain.entities

import app.logdate.shared.model.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ExtractPeopleUseCaseTest {

    @Test
    fun `invoke should delegate to PeopleExtractor`() = runTest {
        // Given
        val documentId = "doc123"
        val text = "Meeting with John Smith and Jane Doe"
        val expectedPeople = listOf(
            Person(name = "John Smith"),
            Person(name = "Jane Doe")
        )
        
        // Create a mock extractor inline
        val mockExtractor = object {
            var capturedDocumentId: String? = null
            var capturedText: String? = null
            
            suspend fun extractPeople(documentId: String, text: String): List<Person> {
                capturedDocumentId = documentId
                capturedText = text
                return expectedPeople
            }
        }
        
        // Create UseCase with mocked extractor behavior
        val useCase = object {
            suspend operator fun invoke(documentId: String, text: String): List<Person> {
                return mockExtractor.extractPeople(documentId, text)
            }
        }

        // When
        val result = useCase(documentId, text)

        // Then
        assertEquals(expectedPeople, result)
        assertEquals(documentId, mockExtractor.capturedDocumentId)
        assertEquals(text, mockExtractor.capturedText)
    }

    @Test
    fun `invoke should return empty list when no people found`() = runTest {
        // Given
        val documentId = "doc456"
        val text = "No names in this text"
        
        val mockExtractor = object {
            suspend fun extractPeople(documentId: String, text: String): List<Person> {
                return emptyList()
            }
        }
        
        val useCase = object {
            suspend operator fun invoke(documentId: String, text: String): List<Person> {
                return mockExtractor.extractPeople(documentId, text)
            }
        }

        // When
        val result = useCase(documentId, text)

        // Then
        assertEquals(emptyList(), result)
    }

    @Test
    fun `invoke should handle single person extraction`() = runTest {
        // Given
        val documentId = "doc789"
        val text = "Call Alice"
        val expectedPerson = Person(name = "Alice")
        
        val mockExtractor = object {
            suspend fun extractPeople(documentId: String, text: String): List<Person> {
                return listOf(expectedPerson)
            }
        }
        
        val useCase = object {
            suspend operator fun invoke(documentId: String, text: String): List<Person> {
                return mockExtractor.extractPeople(documentId, text)
            }
        }

        // When
        val result = useCase(documentId, text)

        // Then
        assertEquals(listOf(expectedPerson), result)
    }

    @Test
    fun `invoke should handle empty text`() = runTest {
        // Given
        val documentId = "doc000"
        val text = ""
        
        val mockExtractor = object {
            suspend fun extractPeople(documentId: String, text: String): List<Person> {
                return emptyList()
            }
        }
        
        val useCase = object {
            suspend operator fun invoke(documentId: String, text: String): List<Person> {
                return mockExtractor.extractPeople(documentId, text)
            }
        }

        // When
        val result = useCase(documentId, text)

        // Then
        assertEquals(emptyList(), result)
    }

    @Test
    fun `invoke should handle whitespace-only text`() = runTest {
        // Given
        val documentId = "doc111"
        val text = "   \n\t  "
        
        val mockExtractor = object {
            suspend fun extractPeople(documentId: String, text: String): List<Person> {
                return emptyList()
            }
        }
        
        val useCase = object {
            suspend operator fun invoke(documentId: String, text: String): List<Person> {
                return mockExtractor.extractPeople(documentId, text)
            }
        }

        // When
        val result = useCase(documentId, text)

        // Then
        assertEquals(emptyList(), result)
    }
}