@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.shared.model.RegisterDeviceRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class PostgreSQLAccountDeviceRepositoryTest {
    @Test
    fun `devices survive repository recreation without duplicate registration`() =
        withH2Database(AccountsTable, AccountDevicesTable) {
            val owner = Uuid.random()
            val other = Uuid.random()
            transaction {
                for ((index, id) in listOf(owner, other).withIndex()) {
                    AccountsTable.insert {
                        it[AccountsTable.id] = id.toJavaUUID()
                        it[username] = "registry-$index"
                        it[displayName] = "Test account"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
            }
            runBlocking {
                val id = Uuid.random()
                val request = RegisterDeviceRequest("Test device", "MACOS", "0.1.0")
                val repository = PostgreSQLAccountDeviceRepository()
                repository.register(owner, id, request, 1000)
                repository.register(owner, id, request.copy(name = "Renamed device"), 2000)
                val reopened = PostgreSQLAccountDeviceRepository()
                val device = reopened.list(owner).single()
                assertEquals(id.toString(), device.id)
                assertEquals("Renamed device", device.name)
                assertEquals(1000, device.createdAt)
                assertEquals(2000, device.lastActive)
                assertEquals(emptyList(), reopened.list(other))
            }
        }
}
