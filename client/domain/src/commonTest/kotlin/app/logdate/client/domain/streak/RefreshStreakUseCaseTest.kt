package app.logdate.client.domain.streak

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RefreshStreakUseCaseTest {
    @Test
    fun `refresh calculates streak and writes to cache`() =
        runTest {
            val today = LocalDate(2026, 3, 31)
            val instant = today.atStartOfDayIn(TimeZone.UTC)
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = instant,
                    lastUpdated = instant,
                    content = "Test",
                )
            val repository = FakeNotesRepository(mapOf(today to listOf(note)))
            val settingsRepo = FakeStreakSettingsRepository()

            val refreshUseCase =
                RefreshStreakUseCase(
                    CalculateStreakUseCase(repository),
                    settingsRepo,
                )
            refreshUseCase()

            // Streak should be at least 1 since today has an entry
            assertEquals(1, settingsRepo.getCachedStreak())
        }

    @Test
    fun `refresh writes 0 when no entries exist`() =
        runTest {
            val repository = FakeNotesRepository(emptyMap())
            val settingsRepo = FakeStreakSettingsRepository()

            val refreshUseCase =
                RefreshStreakUseCase(
                    CalculateStreakUseCase(repository),
                    settingsRepo,
                )
            refreshUseCase()

            assertEquals(0, settingsRepo.getCachedStreak())
        }

    private class FakeStreakSettingsRepository : StreakSettingsRepository {
        private val enabledFlow = MutableStateFlow(true)
        private val cachedStreakFlow = MutableStateFlow(0)

        override fun observeStreakEnabled(): Flow<Boolean> = enabledFlow

        override suspend fun isStreakEnabled(): Boolean = enabledFlow.value

        override suspend fun setStreakEnabled(enabled: Boolean) {
            enabledFlow.value = enabled
        }

        override fun observeCachedStreak(): Flow<Int> = cachedStreakFlow

        override suspend fun getCachedStreak(): Int = cachedStreakFlow.value

        override suspend fun setCachedStreak(value: Int) {
            cachedStreakFlow.value = value
        }
    }
}
