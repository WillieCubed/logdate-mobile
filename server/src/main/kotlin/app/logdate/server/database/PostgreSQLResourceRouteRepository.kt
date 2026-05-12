package app.logdate.server.database

import app.logdate.server.logdate.ResourceKind
import app.logdate.server.logdate.ResourceRoute
import app.logdate.server.logdate.ResourceRouteRepository
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

class PostgreSQLResourceRouteRepository : ResourceRouteRepository {
    override suspend fun find(resourceId: String): ResourceRoute? =
        transaction {
            ResourceRoutesTable
                .selectAll()
                .where { ResourceRoutesTable.resourceId eq resourceId }
                .singleOrNull()
                ?.let { row ->
                    ResourceRoute(
                        resourceId = row[ResourceRoutesTable.resourceId],
                        accountId = row[ResourceRoutesTable.accountId],
                        kind = ResourceKind.valueOf(row[ResourceRoutesTable.kind].uppercase()),
                    )
                }
        }

    override suspend fun upsert(route: ResourceRoute): ResourceRoute =
        transaction {
            val now = Clock.System.now()
            ResourceRoutesTable.upsert(ResourceRoutesTable.resourceId) {
                it[resourceId] = route.resourceId
                it[accountId] = route.accountId
                it[kind] = route.kind.pathSegment
                it[updatedAt] = now
                it[createdAt] = now
            }
            route
        }

    override suspend fun delete(resourceId: String) {
        transaction {
            ResourceRoutesTable.deleteWhere { ResourceRoutesTable.resourceId eq resourceId }
        }
    }
}

object ResourceRoutesTable : Table("resource_routes") {
    val resourceId = text("resource_id")
    val accountId = javaUUID("account_id").references(AccountsTable.id)
    val kind = varchar("kind", 32)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(resourceId)
}
