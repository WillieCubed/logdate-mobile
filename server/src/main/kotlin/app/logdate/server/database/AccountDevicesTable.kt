package app.logdate.server.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object AccountDevicesTable : Table("account_devices") {
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceId = javaUUID("device_id")
    val name = varchar("name", 120)
    val platform = varchar("platform", 16)
    val appVersion = varchar("app_version", 64)
    val createdAt = long("created_at")
    val lastActive = long("last_active")

    override val primaryKey = PrimaryKey(accountId, deviceId)
}
