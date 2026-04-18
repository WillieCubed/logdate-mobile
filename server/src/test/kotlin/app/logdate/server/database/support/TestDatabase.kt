package app.logdate.server.database.support

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun withH2Database(
    vararg tables: Table,
    block: () -> Unit,
) {
    val dbName = "server_test_${System.nanoTime()}"
    Database.connect(
        url = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        driver = "org.h2.Driver",
        user = "sa",
        password = "",
    )

    transaction {
        SchemaUtils.create(*tables)
    }

    try {
        block()
    } finally {
        transaction {
            SchemaUtils.drop(*tables)
        }
    }
}
