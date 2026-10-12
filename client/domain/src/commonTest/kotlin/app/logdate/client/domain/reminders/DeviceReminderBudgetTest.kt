package app.logdate.client.domain.reminders

import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceReminderBudgetTest {
    private val storage = FakeKeyValueStorage()
    private val budget = DeviceReminderBudget(storage)
    private val today = LocalDate(2026, 10, 12)

    @Test
    fun corruptBudgetCannotBeResetToPermitAnotherPost() =
        runTest {
            storage.putString(DeviceReminderBudget.STORAGE_KEY, "unreadable")
            assertFailsWith<IllegalStateException> { budget.available(today) }
            assertFailsWith<IllegalStateException> { budget.consume(today) }
            assertEquals("unreadable", storage.getString(DeviceReminderBudget.STORAGE_KEY))
        }

    @Test
    fun standaloneAndPairedPostsShareTheSameDailyLimit() =
        runTest {
            assertTrue(budget.available(today))
            assertTrue(budget.consume(today))
            assertFalse(budget.available(today))
            assertFalse(budget.consume(today))
        }

    @Test
    fun reopeningCannotAuthorizeAnotherPostToday() =
        runTest {
            assertTrue(budget.consume(today))
            assertFalse(DeviceReminderBudget(storage).consume(today))
        }

    @Test
    fun tomorrowGetsAnotherReminderButClockRollbackDoesNot() =
        runTest {
            assertTrue(budget.consume(today))
            assertFalse(budget.consume(LocalDate(2026, 10, 11)))
            assertTrue(budget.consume(LocalDate(2026, 10, 13)))
        }

    @Test
    fun overlappingDeliveryPathsAuthorizeOnePost() =
        runTest {
            assertEquals(1, (1..20).map { async { budget.consume(today) } }.awaitAll().count { it })
        }
}
