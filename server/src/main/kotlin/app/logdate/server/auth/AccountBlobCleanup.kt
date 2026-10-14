package app.logdate.server.auth

import app.logdate.server.database.DatabaseConfig
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import io.github.aakira.napier.Napier
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** Durable deletion work, independent of the account's cascading rows. */
@OptIn(ExperimentalUuidApi::class)
class AccountBlobCleanup(
    dataSource: DataSource? = null,
    private val storage: LogDateBlobStorage,
) {
    private val source: DataSource by lazy {
        dataSource ?: checkNotNull(DatabaseConfig.initializedDataSource) {
            "Account cleanup database has not been initialized"
        }
    }

    /** One bounded drain. A failed batch exits unsuccessfully so the scheduled job can alert. */
    fun runOnce(ownerId: UUID? = null): Int {
        var processed = 0
        var failures = 0
        for (attempt in 1..100) {
            val claim = claim(ownerId) ?: break
            try {
                clean(claim)
                processed++
            } catch (_: Exception) {
                failures++
                release(claim, succeeded = false)
                // Provider exception messages may contain object names or request payloads.
                Napier.e("Account blob cleanup failed")
            }
        }
        val overdue =
            source.connection.use { connection ->
                connection.autoCommit = true
                connection.createStatement().use { sql ->
                    sql
                        .executeQuery(
                            "SELECT count(*) FROM account_blob_cleanup WHERE completed_at IS NULL AND created_at < now()-interval '24 hours'",
                        ).use {
                            it.next()
                            it.getLong(1)
                        }
                }
            }
        if (overdue > 0) Napier.e("Account blob cleanup queue overdue")
        check(failures == 0) { "Account blob cleanup failed; durable work retained" }
        Napier.i("Account blob cleanup batch complete")
        return processed
    }

    private data class Claim(
        val ownerId: UUID,
        val token: UUID,
    )

    private fun claim(ownerId: UUID?): Claim? =
        source.connection.use { connection ->
            connection.autoCommit = true
            val token = Uuid.random().toJavaUuid()
            connection
                .prepareStatement(
                    """UPDATE account_blob_cleanup SET lease_token=?, lease_until=now()+interval '15 minutes'
            WHERE owner_id=(SELECT owner_id FROM account_blob_cleanup
                WHERE completed_at IS NULL AND next_attempt_at<=now()
                  AND (lease_until IS NULL OR lease_until<now())
                  AND (?::uuid IS NULL OR owner_id=?::uuid)
                ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1)
            RETURNING owner_id""",
                ).use { sql ->
                    sql.setObject(1, token)
                    sql.setObject(2, ownerId)
                    sql.setObject(3, ownerId)
                    sql.executeQuery().use { if (it.next()) Claim(it.getObject(1, UUID::class.java), token) else null }
                }
        }

    private fun clean(claim: Claim) {
        val persisted =
            source.connection.use { connection ->
                connection.autoCommit = true
                renewAndCheckOwner(connection, claim)
                connection.prepareStatement("SELECT storage_path FROM account_blob_cleanup_paths WHERE owner_id=?").use { sql ->
                    sql.setObject(1, claim.ownerId)
                    sql.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
                }
            }
        // The marker survives initial cleanup so uploads already in progress can land safely.
        // Enumerate before any deletes: an unavailable listing cannot produce completion.
        val paths = (persisted + storage.listOwnerBlobPaths(claim.ownerId)).distinct()
        paths.forEach { path ->
            require(ownsPath(claim.ownerId, path)) { "Cleanup path is outside its owner namespace" }
            source.connection.use {
                it.autoCommit = true
                renewAndCheckOwner(it, claim)
            }
            storage.deleteBlobIfExists(path)
            source.connection.use { connection ->
                connection.autoCommit = true
                connection
                    .prepareStatement(
                        """DELETE FROM account_blob_cleanup_paths WHERE owner_id=? AND storage_path=?
                    AND EXISTS (SELECT 1 FROM account_blob_cleanup WHERE owner_id=? AND lease_token=?)""",
                    ).use { sql ->
                        sql.setObject(1, claim.ownerId)
                        sql.setString(2, path)
                        sql.setObject(3, claim.ownerId)
                        sql.setObject(4, claim.token)
                        sql.executeUpdate()
                    }
            }
        }
        release(claim, succeeded = true)
    }

    private fun renewAndCheckOwner(
        connection: Connection,
        claim: Claim,
    ) {
        connection
            .prepareStatement(
                """UPDATE account_blob_cleanup SET lease_until=now()+interval '15 minutes'
            WHERE owner_id=? AND lease_token=? AND completed_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM accounts WHERE id=?)""",
            ).use { sql ->
                sql.setObject(1, claim.ownerId)
                sql.setObject(2, claim.token)
                sql.setObject(3, claim.ownerId)
                check(sql.executeUpdate() == 1) { "Cleanup lease lost or owner remains active" }
            }
    }

    private fun release(
        claim: Claim,
        succeeded: Boolean,
    ) {
        source.connection.use { connection ->
            connection.autoCommit = true
            connection
                .prepareStatement(
                    """UPDATE account_blob_cleanup SET lease_token=NULL, lease_until=NULL,
                next_attempt_at=now()+interval '30 minutes',
                completed_at=CASE WHEN ? AND reconcile_until<=now() THEN now() ELSE NULL END
                WHERE owner_id=? AND lease_token=?""",
                ).use { sql ->
                    sql.setBoolean(1, succeeded)
                    sql.setObject(2, claim.ownerId)
                    sql.setObject(3, claim.token)
                    check(sql.executeUpdate() == 1) { "Cleanup lease lost" }
                }
        }
    }

    private fun ownsPath(
        ownerId: UUID,
        path: String,
    ): Boolean =
        !path.split('/').any { it == ".." || it == "." } &&
            (
                path.startsWith("users/$ownerId/") ||
                    LogDateBlobNamespace.entries.any { path.startsWith("${it.name.lowercase()}/$ownerId/") }
            )
}
