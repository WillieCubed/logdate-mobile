package app.logdate.server.database

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountStorageTotalsTest {
    private val logged = mutableListOf<Pair<LogLevel, String>>()

    private val antilog =
        object : Antilog() {
            override fun performLog(
                priority: LogLevel,
                tag: String?,
                throwable: Throwable?,
                message: String?,
            ) {
                logged += priority to message.orEmpty()
            }
        }

    @AfterTest
    fun uninstall() = Napier.takeLogarithm()

    @Test
    fun `a total that stays at or above zero is logged as nothing`() {
        Napier.base(antilog)
        val account = UUID.randomUUID()

        assertEquals(70L, nonNegativeTotal(account, StorageKind.MEDIA, current = 100, delta = -30))
        assertEquals(0L, nonNegativeTotal(account, StorageKind.BACKUP, current = 30, delta = -30))

        assertEquals(emptyList(), logged)
    }

    @Test
    fun `a total that would go below zero is stored as zero and logged with its account, kind and numbers`() {
        Napier.base(antilog)
        val account = UUID.randomUUID()

        assertEquals(0L, nonNegativeTotal(account, StorageKind.SPACE, current = 10, delta = -25))

        val (level, message) = logged.single()
        assertEquals(LogLevel.ERROR, level)
        assertTrue(listOf("account=$account", "kind=SPACE", "current=10", "delta=-25").all { it in message }, message)
    }
}
