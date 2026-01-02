# Unit Testing Guide

Unit tests verify individual functions and classes in isolation. They should be fast, deterministic, and focused on a single responsibility.

## Scope

Unit tests cover:
- Use cases and business logic
- Domain entities and value objects
- Repository implementations (with mocked data sources)
- State management and view models
- Utility functions and extensions

## Structure

### Basic Test Template

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class MyUseCaseTest {

    private lateinit var useCase: MyUseCase
    private lateinit var fakeRepository: FakeMyRepository

    @Before
    fun setup() {
        fakeRepository = FakeMyRepository()
        useCase = MyUseCase(fakeRepository)
    }

    @Test
    fun givenValidInput_whenExecuting_thenReturnsExpectedResult() {
        // Arrange
        val input = TestData.validInput()

        // Act
        val result = useCase.execute(input)

        // Assert
        assertEquals(expected = TestData.expectedOutput(), actual = result)
    }

    @Test
    fun givenInvalidInput_whenExecuting_thenThrowsException() {
        // Arrange
        val input = TestData.invalidInput()

        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(input)
        }
    }
}
```

## Test Doubles

### Fakes (Preferred)
Full working implementations for testing:

```kotlin
// src/commonTest/kotlin/fakes/FakeJournalRepository.kt
class FakeJournalRepository : JournalRepository {
    private val journals = mutableListOf<Journal>()

    override suspend fun createJournal(name: String): Result<Journal> {
        val journal = Journal(id = Uuid.random(), name = name)
        journals.add(journal)
        return Result.success(journal)
    }

    override suspend fun getJournals(): Result<List<Journal>> {
        return Result.success(journals.toList())
    }

    fun setJournals(list: List<Journal>) {
        journals.clear()
        journals.addAll(list)
    }
}
```

Usage:
```kotlin
class GetJournalsUseCaseTest {
    private lateinit var repository: FakeJournalRepository
    private lateinit var useCase: GetJournalsUseCase

    @Before
    fun setup() {
        repository = FakeJournalRepository()
        useCase = GetJournalsUseCase(repository)
    }

    @Test
    fun whenFetchingJournals_thenReturnsAllJournals() {
        // Arrange
        val journals = listOf(
            TestData.journal("Personal"),
            TestData.journal("Work")
        )
        repository.setJournals(journals)

        // Act
        val result = useCase.execute()

        // Assert
        assertEquals(journals, result.getOrNull())
    }
}
```

### Mocks
Verify interactions using Mockk:

```kotlin
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import io.mockk.coEvery

class SaveJournalUseCaseTest {
    private val repository: JournalRepository = mockk()
    private val useCase = SaveJournalUseCase(repository)

    @Test
    fun whenSavingJournal_thenCallsRepository() {
        // Arrange
        val journal = TestData.journal()
        coEvery { repository.saveJournal(any()) } returns Result.success(journal)

        // Act
        useCase.execute(journal)

        // Assert
        coVerify { repository.saveJournal(journal) }
    }
}
```

## Testing Coroutines

Use `runTest` for suspend functions:

```kotlin
import kotlinx.coroutines.test.runTest

class SaveNoteUseCaseTest {
    @Test
    fun whenSavingNote_thenEmitsStateUpdates() = runTest {
        // Arrange
        val repository = FakeNoteRepository()
        val stateFlow = MutableStateFlow<State>(State.Idle)
        val useCase = SaveNoteUseCase(repository) { state ->
            stateFlow.value = state
        }

        // Act
        useCase.execute(TestData.note())

        // Assert
        assertEquals(State.Success, stateFlow.value)
    }
}
```

## Testing Flows

```kotlin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class GetTimelineFlowTest {
    @Test
    fun whenEmitting_thenCollectsAllValues() = runTest {
        // Arrange
        val repository = FakeTimelineRepository()
        repository.setEntries(TestData.entries())
        val useCase = GetTimelineFlow(repository)

        // Act
        val values = useCase.execute().toList()

        // Assert
        assertEquals(3, values.size)
        assert(values.first() is TimelineState.Loading)
        assert(values.last() is TimelineState.Success)
    }
}
```

## Testing State Management

```kotlin
class TimelineViewModelTest {
    @Test
    fun whenLoadingTimeline_thenStateUpdates() = runTest {
        // Arrange
        val repository = FakeTimelineRepository()
        val viewModel = TimelineViewModel(repository)

        // Act
        viewModel.loadTimeline()
        advanceUntilIdle()

        // Assert
        assertEquals(TimelineState.Success, viewModel.state.value)
    }
}
```

## Error Testing

Test both success and failure paths:

```kotlin
class FetchUserUseCaseTest {
    private val repository: UserRepository = mockk()
    private val useCase = FetchUserUseCase(repository)

    @Test
    fun givenUserExists_thenReturnsUser() = runTest {
        // Arrange
        val user = TestData.user()
        coEvery { repository.getUser(any()) } returns Result.success(user)

        // Act
        val result = useCase.execute("user-id")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun givenNetworkError_thenReturnsFailure() = runTest {
        // Arrange
        val exception = IOException("Network error")
        coEvery { repository.getUser(any()) } returns Result.failure(exception)

        // Act
        val result = useCase.execute("user-id")

        // Assert
        assertTrue(result.isFailure)
        assertIs<IOException>(result.exceptionOrNull())
    }
}
```

## Testing Data Transformations

```kotlin
class JournalMapperTest {
    @Test
    fun whenMappingDatabaseEntity_thenConvertsToModel() {
        // Arrange
        val entity = JournalEntity(
            id = "123",
            name = "My Journal",
            createdAt = Instant.parse("2024-01-15T10:00:00Z")
        )

        // Act
        val model = entity.toModel()

        // Assert
        assertEquals("123", model.id)
        assertEquals("My Journal", model.name)
    }
}
```

## Parameterized Tests

Test multiple inputs with single test method:

```kotlin
import kotlin.test.ParameterizedTest
import kotlin.test.ValueSource

class ValidateEmailTest {
    @ParameterizedTest
    @ValueSource(strings = [
        "valid@example.com",
        "test+tag@domain.co.uk",
        "user.name@company.com"
    ])
    fun givenValidEmails_thenReturnsTrue(email: String) {
        assertTrue(isValidEmail(email))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "invalid",
        "@example.com",
        "user@"
    ])
    fun givenInvalidEmails_thenReturnsFalse(email: String) {
        assertFalse(isValidEmail(email))
    }
}
```

## Test Data Builders

Create reusable test data:

```kotlin
object TestData {
    fun journal(
        id: String = Uuid.random().toString(),
        name: String = "Test Journal"
    ) = Journal(id = id, name = name)

    fun note(
        id: String = Uuid.random().toString(),
        journalId: String = journal().id,
        content: String = "Test content"
    ) = Note(id = id, journalId = journalId, content = content)

    fun entries(count: Int = 5) = (1..count).map { i ->
        note(content = "Entry $i")
    }
}
```

## Running Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific module
./gradlew :client:domain:test

# Run specific test class
./gradlew test --tests "MyUseCaseTest"

# Run specific test method
./gradlew test --tests "MyUseCaseTest.testMethodName"
```

## Best Practices

1. **One Assertion Per Test**: When possible, keeps tests focused
   ```kotlin
   // Good
   @Test
   fun whenSaving_thenReturnsSuccess() { }

   @Test
   fun whenSavingWithInvalidData_thenThrows() { }

   // Avoid
   @Test
   fun whenSaving_thenVerifyAllBehavior() {
       // Multiple assertions mixing concerns
   }
   ```

2. **Meaningful Test Names**: Describe scenario and expected behavior
   ```kotlin
   // Good
   fun givenEmptyJournal_whenFetching_thenReturnsEmpty()
   fun givenNetworkError_whenFetching_thenReturnsCachedData()

   // Avoid
   fun testFetch()
   fun testError()
   ```

3. **Use Test Fixtures**: Share common setup
   ```kotlin
   abstract class UseCaseTest {
       protected val repository = FakeRepository()
       protected val clock = TestClock()
   }

   class MyUseCaseTest : UseCaseTest() { }
   ```

4. **Keep Tests Independent**: No test should depend on another
   ```kotlin
   // Good - each test can run independently
   @Test
   fun test1() { }

   @Test
   fun test2() { }

   // Avoid - test2 depends on test1
   @Test
   fun test1() { }

   @Test
   fun test2() { } // Fails if test1 didn't run
   ```

5. **Mock External Dependencies**: Database, network, system
   ```kotlin
   class CreateJournalUseCaseTest {
       private val fakeRepository = FakeJournalRepository()
       private val mockClock = mockk<Clock>()

       private val useCase = CreateJournalUseCase(fakeRepository, mockClock)
   }
   ```

## Coverage Goals

Target coverage by layer:
- **Domain (Use Cases)**: 80%+
- **Data (Repositories)**: 70%+
- **UI (ViewModels)**: 60%+
- **Utilities**: 90%+

Check coverage with:
```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```
