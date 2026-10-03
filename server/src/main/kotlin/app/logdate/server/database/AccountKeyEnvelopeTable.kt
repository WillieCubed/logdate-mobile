package app.logdate.server.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object AccountKeyEnvelopeTable : Table("account_key_envelopes") {
    val credentialId = text("credential_id").references(PasskeysTable.credentialId, onDelete = ReferenceOption.CASCADE)
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val ciphertext = varchar("ciphertext", 132)
    override val primaryKey = PrimaryKey(credentialId)
}
