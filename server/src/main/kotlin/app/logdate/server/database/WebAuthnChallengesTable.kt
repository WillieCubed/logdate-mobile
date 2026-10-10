package app.logdate.server.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object WebAuthnChallengesTable : Table("webauthn_challenges") {
    val challenge = text("challenge")
    val userId = javaUUID("user_id")
    val type = varchar("challenge_type", 20)
    val expiresAt = timestamp("expires_at")
    val isUsed = bool("is_used").default(false)

    override val primaryKey = PrimaryKey(challenge)
}
