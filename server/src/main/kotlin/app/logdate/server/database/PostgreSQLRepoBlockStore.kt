package app.logdate.server.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoBlock
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoCommit
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.SignedRepoCommit
import kotlin.time.Clock

/**
 * Exposed-backed repo block store for the server's durable AT Protocol mirror.
 *
 * Repo ownership is keyed by DID string rather than a foreign-key account id so the shared repo
 * library can keep speaking pure AT Protocol identifiers.
 */
class PostgreSQLRepoBlockStore : RepoBlockStore {
    override suspend fun readHead(repo: AtprotoDid): Result<RepoHead?> =
        runCatching {
            transaction {
                AtprotoRepoHeadsTable
                    .selectAll()
                    .where { AtprotoRepoHeadsTable.repoDid eq repo.toString() }
                    .singleOrNull()
                    ?.toRepoHead()
            }
        }

    override suspend fun writeHead(head: RepoHead): Result<Unit> =
        runCatching {
            transaction {
                val repoDid = head.repo.toString()
                val existing =
                    AtprotoRepoHeadsTable
                        .selectAll()
                        .where { AtprotoRepoHeadsTable.repoDid eq repoDid }
                        .singleOrNull()

                if (existing == null) {
                    AtprotoRepoHeadsTable.insert {
                        it[AtprotoRepoHeadsTable.repoDid] = repoDid
                        it[rootCid] = head.root.toString()
                        it[commitCid] = head.commitCid.toString()
                        it[revision] = head.revision
                        it[updatedAt] = Clock.System.now()
                    }
                } else {
                    AtprotoRepoHeadsTable.update({ AtprotoRepoHeadsTable.repoDid eq repoDid }) {
                        it[rootCid] = head.root.toString()
                        it[commitCid] = head.commitCid.toString()
                        it[revision] = head.revision
                        it[updatedAt] = Clock.System.now()
                    }
                }
            }
        }

    override suspend fun readBlock(cid: Cid): Result<RepoBlock?> =
        runCatching {
            transaction {
                AtprotoRepoBlocksTable
                    .selectAll()
                    .where { AtprotoRepoBlocksTable.cid eq cid.toString() }
                    .singleOrNull()
                    ?.toRepoBlock()
            }
        }

    override suspend fun writeBlock(
        repo: AtprotoDid,
        block: RepoBlock,
    ): Result<Unit> =
        runCatching {
            transaction {
                val cidValue = block.cid.toString()
                val existingBlock =
                    AtprotoRepoBlocksTable
                        .selectAll()
                        .where { AtprotoRepoBlocksTable.cid eq cidValue }
                        .singleOrNull()
                if (existingBlock == null) {
                    AtprotoRepoBlocksTable.insert {
                        it[cid] = cidValue
                        it[bytes] = block.bytes
                    }
                } else {
                    AtprotoRepoBlocksTable.update({ AtprotoRepoBlocksTable.cid eq cidValue }) {
                        it[bytes] = block.bytes
                    }
                }

                val existingLink =
                    AtprotoRepoBlockLinksTable
                        .selectAll()
                        .where {
                            (AtprotoRepoBlockLinksTable.repoDid eq repo.toString()) and
                                (AtprotoRepoBlockLinksTable.cid eq cidValue)
                        }.singleOrNull()
                if (existingLink == null) {
                    AtprotoRepoBlockLinksTable.insert {
                        it[repoDid] = repo.toString()
                        it[cid] = cidValue
                    }
                }
            }
        }

    override suspend fun clearRepo(repo: AtprotoDid): Result<Unit> =
        runCatching {
            transaction {
                val repoDid = repo.toString()
                AtprotoRepoHeadsTable.deleteWhere { AtprotoRepoHeadsTable.repoDid eq repoDid }
                AtprotoRepoBlockLinksTable.deleteWhere { AtprotoRepoBlockLinksTable.repoDid eq repoDid }
                AtprotoRepoCommitsTable.deleteWhere { AtprotoRepoCommitsTable.repoDid eq repoDid }
            }
        }

    override suspend fun listBlocks(repo: AtprotoDid): Result<List<RepoBlock>> =
        runCatching {
            transaction {
                AtprotoRepoBlockLinksTable
                    .innerJoin(AtprotoRepoBlocksTable)
                    .selectAll()
                    .where { AtprotoRepoBlockLinksTable.repoDid eq repo.toString() }
                    .orderBy(AtprotoRepoBlocksTable.cid, SortOrder.ASC)
                    .map { row ->
                        RepoBlock(
                            cid = Cid.require(row[AtprotoRepoBlocksTable.cid]),
                            bytes = row[AtprotoRepoBlocksTable.bytes],
                        )
                    }
            }
        }

    override suspend fun appendCommit(
        repo: AtprotoDid,
        commit: SignedRepoCommit,
    ): Result<Unit> =
        runCatching {
            transaction {
                val existing =
                    AtprotoRepoCommitsTable
                        .selectAll()
                        .where {
                            (AtprotoRepoCommitsTable.repoDid eq repo.toString()) and
                                (AtprotoRepoCommitsTable.revision eq commit.commit.revision)
                        }.singleOrNull()
                if (existing == null) {
                    AtprotoRepoCommitsTable.insert {
                        it[repoDid] = repo.toString()
                        it[revision] = commit.commit.revision
                        it[cid] = commit.cid.toString()
                        it[rootCid] = commit.commit.root.toString()
                        it[prevCid] = commit.commit.prev?.toString()
                        it[createdAtEpochMillis] = commit.commit.createdAtEpochMillis
                        it[recordCount] = commit.commit.recordCount
                        it[signature] = commit.signature
                    }
                } else {
                    AtprotoRepoCommitsTable.update({
                        (AtprotoRepoCommitsTable.repoDid eq repo.toString()) and
                            (AtprotoRepoCommitsTable.revision eq commit.commit.revision)
                    }) {
                        it[cid] = commit.cid.toString()
                        it[rootCid] = commit.commit.root.toString()
                        it[prevCid] = commit.commit.prev?.toString()
                        it[createdAtEpochMillis] = commit.commit.createdAtEpochMillis
                        it[recordCount] = commit.commit.recordCount
                        it[signature] = commit.signature
                    }
                }
            }
        }

    override suspend fun listCommits(repo: AtprotoDid): Result<List<SignedRepoCommit>> =
        runCatching {
            transaction {
                AtprotoRepoCommitsTable
                    .selectAll()
                    .where { AtprotoRepoCommitsTable.repoDid eq repo.toString() }
                    .orderBy(AtprotoRepoCommitsTable.revision, SortOrder.ASC)
                    .map(ResultRow::toSignedRepoCommit)
            }
        }
}

private fun ResultRow.toRepoHead(): RepoHead =
    RepoHead(
        repo = AtprotoDid.require(this[AtprotoRepoHeadsTable.repoDid]),
        root = Cid.require(this[AtprotoRepoHeadsTable.rootCid]),
        commitCid = Cid.require(this[AtprotoRepoHeadsTable.commitCid]),
        revision = this[AtprotoRepoHeadsTable.revision],
    )

private fun ResultRow.toRepoBlock(): RepoBlock =
    RepoBlock(
        cid = Cid.require(this[AtprotoRepoBlocksTable.cid]),
        bytes = this[AtprotoRepoBlocksTable.bytes],
    )

private fun ResultRow.toSignedRepoCommit(): SignedRepoCommit =
    SignedRepoCommit(
        cid = Cid.require(this[AtprotoRepoCommitsTable.cid]),
        commit =
            RepoCommit(
                repo = AtprotoDid.require(this[AtprotoRepoCommitsTable.repoDid]),
                root = Cid.require(this[AtprotoRepoCommitsTable.rootCid]),
                prev = this[AtprotoRepoCommitsTable.prevCid]?.let(Cid::require),
                revision = this[AtprotoRepoCommitsTable.revision],
                createdAtEpochMillis = this[AtprotoRepoCommitsTable.createdAtEpochMillis],
                recordCount = this[AtprotoRepoCommitsTable.recordCount],
            ),
        signature = this[AtprotoRepoCommitsTable.signature],
    )
