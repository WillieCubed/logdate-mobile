package app.logdate.server.auth

import javax.sql.DataSource

/** Production must not accept account deletion on a schema that can lose object pointers. */
fun requireAccountBlobCleanupSchema(source: DataSource) {
    source.connection.use { connection ->
        connection.autoCommit = true
        connection.createStatement().use { sql ->
            sql
                .executeQuery(
                    """SELECT to_regclass('account_blob_cleanup_paths') IS NOT NULL
                AND (SELECT count(*) FROM pg_trigger WHERE tgrelid='accounts'::regclass
                    AND tgname IN ('accounts_enqueue_blob_cleanup', 'accounts_prevent_deleted_id_reuse')
                    AND tgenabled IN ('O', 'A')) = 2""",
                ).use { result ->
                    check(result.next() && result.getBoolean(1)) { "Required account blob cleanup schema is unavailable" }
                }
        }
    }
}
