package app.logdate.server.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object DeviceEnrollmentTable : Table("device_enrollments") {
    val id = javaUUID("id")
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val deviceName = varchar("device_name", 120)
    val publicKey = varchar("public_key", 128)
    val confirmationCode = varchar("confirmation_code", 6)
    val encryptedEnvelope = text("encrypted_envelope").nullable()
    val claimHash = varchar("claim_hash", 64).nullable()
    val status = varchar("status", 16)
    val expiresAt = long("expires_at")
    val sessionIssued = bool("session_issued").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_device_enrollments_account", false, accountId)
        index("idx_device_enrollments_expires", false, expiresAt)
        uniqueIndex("idx_device_enrollments_claim", claimHash)
    }
}
