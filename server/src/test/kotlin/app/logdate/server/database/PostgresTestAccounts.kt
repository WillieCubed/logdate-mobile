package app.logdate.server.database

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Gives a disposable-PostgreSQL test an account row for [id]. The sync index refers to accounts,
 * so tests that keep their accounts in memory still need the row in the database.
 */
internal fun insertTestAccountRow(id: UUID) {
    transaction {
        exec(
            "INSERT INTO accounts(id, username, display_name, created_at) " +
                "VALUES ('$id', 't${id.toString().replace("-", "").take(20)}', 'Test', now())",
        )
    }
}

/** Deletes the row [insertTestAccountRow] added, and with it the account's sync index. */
internal fun deleteTestAccountRow(id: UUID) {
    transaction { exec("DELETE FROM accounts WHERE id = '$id'") }
}
