package app.logdate.server.logdate

import app.logdate.server.atproto.HostedRepoCommitSigner
import app.logdate.server.auth.AccountRepository
import app.logdate.server.identity.SigningKeyService
import io.github.aakira.napier.Napier
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.syntax.RecordKey
import java.util.UUID

/**
 * Marked moments in the account's repo, versioned and tombstoned through the same metadata store as entries:
 * they share the account's version sequence and the scheduled tombstone purge. [repoDidFor] is
 * [RepoBackedLogDateCollectionsRepository.canonicalRepoDid], so both stores write to one repo.
 */
internal class RepoBackedLogDateMomentsRepository(
    accountRepository: AccountRepository,
    signingKeyService: SigningKeyService,
    blockStore: RepoBlockStore,
    private val metadataStore: LogDateCollectionsMetadataStore,
    private val repoDidFor: suspend (UUID) -> AtprotoDid,
) : LogDateMomentsRepository {
    private val repoEngine = DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))
    private val conditionalWriter = RepoConditionalWriter(repoEngine, metadataStore)

    override suspend fun momentState(
        userId: UUID,
        id: String,
    ): LogDateRecordState = conditionalWriter.state(userId, LogDateCollectionKind.MOMENT, id)

    override suspend fun upsertMoment(
        userId: UUID,
        moment: LogDateMoment,
    ): LogDateMoment {
        val repoDid = repoDidFor(userId)
        repoEngine.putRecord(momentRecordId(repoDid, moment.id), moment.toRepoJson()).getOrThrow()
        val metadata = metadataStore.upsert(userId, repoDid, LogDateCollectionKind.MOMENT, moment.id)
        return moment.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun createMomentIfAbsent(
        userId: UUID,
        moment: LogDateMoment,
        respectTombstones: Boolean,
    ): ConditionalCreate<LogDateMoment> {
        val repoDid = repoDidFor(userId)
        return conditionalWriter
            .createIfAbsent(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.MOMENT,
                recordId = momentRecordId(repoDid, moment.id),
                value = moment.toRepoJson(),
                respectTombstones = respectTombstones,
            ).mapCreated { version -> moment.copy(version = version, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun getMoment(
        userId: UUID,
        id: String,
    ): LogDateMoment? {
        val metadata = metadataStore.metadata(userId, LogDateCollectionKind.MOMENT, id) ?: return null
        return repoEngine
            .getRecord(momentRecordId(repoDidFor(userId), id))
            .getOrThrow()
            ?.value
            ?.toLogDateMoment(RecordKey.require(id), metadata.version)
    }

    override suspend fun deleteMoment(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        val existing = metadataStore.metadata(userId, LogDateCollectionKind.MOMENT, id) ?: return
        val repoDid = repoDidFor(userId)
        if (!repoEngine.deleteRecord(momentRecordId(repoDid, id)).getOrThrow()) {
            Napier.w("A moment's repo record was already missing when it was deleted")
        }
        metadataStore.delete(userId, repoDid, LogDateCollectionKind.MOMENT, existing.recordKey, deletedAt)
    }

    override suspend fun momentChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateMoment, LogDateMomentDeletion> {
        val repoDid = repoDidFor(userId)
        val metadata = metadataStore.changes(userId, LogDateCollectionKind.MOMENT, since, limit)
        val records = repoEngine.getRecords(metadata.changes.map { momentRecordId(repoDid, it.recordKey) }).getOrThrow()
        return LogDateChangeSet(
            changes =
                records.zip(metadata.changes).mapNotNull { (record, change) ->
                    record?.value?.toLogDateMoment(RecordKey.require(change.recordKey), change.version)
                },
            deletions = metadata.deletions.map { LogDateMomentDeletion(it.recordKey, requireNotNull(it.deletedAt), it.version) },
            lastTimestamp = metadata.lastTimestamp,
            hasMore = metadata.hasMore,
        )
    }
}
