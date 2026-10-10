@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.logdate

import app.logdate.server.atproto.HostedRepoCommitSigner
import app.logdate.server.auth.AccountRepository
import app.logdate.server.database.toKotlinUuid
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.SigningKeyService
import io.github.aakira.napier.Napier
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InvalidSwapException
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.RecordKey
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi

/**
 * Canonical LogDate collections repository backed by the shared AT Protocol repo engine.
 *
 * Live record bodies are stored in the repo block store. Sync-only concerns such as versions,
 * tombstones, and the stable repo DID binding stay in [metadataStore] so the server's internal
 * language remains LogDate collections rather than raw AT Protocol records.
 */
internal class RepoBackedLogDateCollectionsRepository(
    private val accountRepository: AccountRepository,
    private val identityService: AtprotoIdentityService,
    signingKeyService: SigningKeyService,
    blockStore: RepoBlockStore,
    internal val metadataStore: LogDateCollectionsMetadataStore,
) : LogDateCollectionsRepository {
    internal val repoEngine = DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))

    override suspend fun status(userId: UUID): LogDateCollectionsStatus = metadataStore.status(userId)

    override suspend fun listEntries(userId: UUID): List<LogDateEntry> =
        listLiveRecords(userId = userId, collection = LogDateCollectionKind.ENTRY) { repoDid, metadata ->
            repoEngine
                .getRecord(entryRecordId(repoDid, metadata.recordKey))
                .getOrThrow()
                ?.value
                ?.toLogDateEntry(recordKey = RecordKey.require(metadata.recordKey), version = metadata.version)
        }

    override suspend fun upsertEntry(
        userId: UUID,
        entry: LogDateEntry,
    ): LogDateEntry {
        val repoDid = canonicalRepoDid(userId)
        val preserved =
            entry.copy(
                transcript =
                    entry.transcript ?: getEntry(userId, entry.id)
                        ?.takeIf {
                            it.type == entry.type && it.mediaUri == entry.mediaUri && it.durationMs == entry.durationMs
                        }?.transcript,
            )
        repoEngine.putRecord(entryRecordId(repoDid, entry.id), preserved.toRepoJson()).getOrThrow()
        val metadata =
            metadataStore.upsert(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.ENTRY,
                recordKey = entry.id,
            )
        return preserved.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun entryExists(
        userId: UUID,
        id: String,
    ): Boolean = metadataStore.metadata(userId, LogDateCollectionKind.ENTRY, id) != null

    override suspend fun createEntryIfAbsent(
        userId: UUID,
        entry: LogDateEntry,
    ): LogDateEntry? {
        val repoDid = canonicalRepoDid(userId)
        val written = repoEngine.putRecordIfAbsent(entryRecordId(repoDid, entry.id), entry.toRepoJson())
        if (written.exceptionOrNull() is InvalidSwapException) {
            indexExistingRecordIfNeeded(userId, repoDid, LogDateCollectionKind.ENTRY, entryRecordId(repoDid, entry.id))
            return null
        }
        written.getOrThrow()
        val metadata = metadataStore.upsert(userId, repoDid, LogDateCollectionKind.ENTRY, entry.id)
        return entry.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun createJournalIfAbsent(
        userId: UUID,
        journal: LogDateJournal,
    ): LogDateJournal? {
        val repoDid = canonicalRepoDid(userId)
        val written = repoEngine.putRecordIfAbsent(journalRecordId(repoDid, journal.id), journal.toRepoJson())
        if (written.exceptionOrNull() is InvalidSwapException) {
            indexExistingRecordIfNeeded(userId, repoDid, LogDateCollectionKind.JOURNAL, journalRecordId(repoDid, journal.id))
            return null
        }
        written.getOrThrow()
        val metadata = metadataStore.upsert(userId, repoDid, LogDateCollectionKind.JOURNAL, journal.id)
        return journal.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun journalExists(
        userId: UUID,
        id: String,
    ): Boolean = metadataStore.metadata(userId, LogDateCollectionKind.JOURNAL, id) != null

    private suspend fun indexExistingRecordIfNeeded(
        userId: UUID,
        repoDid: AtprotoDid,
        collection: LogDateCollectionKind,
        recordId: RepoRecordId,
    ) {
        val key = recordId.recordKey.toString()
        if (metadataStore.metadata(userId, collection, key) != null) return
        // A process may stop after its canonical write but before indexing it for sync.
        // Rebuild that index from the surviving record; a retry must never replace its body.
        if (repoEngine.getRecord(recordId).getOrThrow() != null) {
            metadataStore.upsert(userId, repoDid, collection, key)
        }
    }

    override suspend fun getEntry(
        userId: UUID,
        id: String,
    ): LogDateEntry? {
        val metadata = metadataStore.metadata(userId, LogDateCollectionKind.ENTRY, id) ?: return null
        val repoDid = canonicalRepoDid(userId)
        return repoEngine
            .getRecord(entryRecordId(repoDid, id))
            .getOrThrow()
            ?.value
            ?.toLogDateEntry(recordKey = RecordKey.require(id), version = metadata.version)
            ?: missingRecord(
                userId = userId,
                collection = LogDateCollectionKind.ENTRY,
                recordKey = id,
            )
    }

    override suspend fun deleteEntry(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        val existing = metadataStore.metadata(userId, LogDateCollectionKind.ENTRY, id) ?: return
        val repoDid = canonicalRepoDid(userId)
        val deleted = repoEngine.deleteRecord(entryRecordId(repoDid, id)).getOrThrow()
        if (!deleted) {
            Napier.w("Expected canonical repo entry $id for user $userId before tombstoning it")
        }
        metadataStore.delete(
            userId = userId,
            repoDid = repoDid,
            collection = LogDateCollectionKind.ENTRY,
            recordKey = existing.recordKey,
            deletedAt = deletedAt,
        )
    }

    override suspend fun entryChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateEntry, LogDateEntryDeletion> {
        val repoDid = canonicalRepoDid(userId)
        val metadata = metadataStore.changes(userId, LogDateCollectionKind.ENTRY, since, limit)
        return LogDateChangeSet(
            changes =
                repoEngine
                    .getRecords(metadata.changes.map { entryRecordId(repoDid, it.recordKey) })
                    .getOrThrow()
                    .zip(metadata.changes)
                    .mapNotNull { (record, change) ->
                        record
                            ?.value
                            ?.toLogDateEntry(recordKey = RecordKey.require(change.recordKey), version = change.version)
                            ?: missingRecord(userId, LogDateCollectionKind.ENTRY, change.recordKey)
                    },
            deletions =
                metadata.deletions.map { deletion ->
                    LogDateEntryDeletion(
                        id = deletion.recordKey,
                        deletedAt = requireNotNull(deletion.deletedAt),
                        serverVersion = deletion.version,
                    )
                },
            lastTimestamp = metadata.lastTimestamp,
            hasMore = metadata.hasMore,
        )
    }

    override suspend fun listJournals(userId: UUID): List<LogDateJournal> =
        listLiveRecords(userId = userId, collection = LogDateCollectionKind.JOURNAL) { repoDid, metadata ->
            repoEngine
                .getRecord(journalRecordId(repoDid, metadata.recordKey))
                .getOrThrow()
                ?.value
                ?.toLogDateJournal(recordKey = RecordKey.require(metadata.recordKey), version = metadata.version)
        }

    override suspend fun upsertJournal(
        userId: UUID,
        journal: LogDateJournal,
    ): LogDateJournal {
        val repoDid = canonicalRepoDid(userId)
        repoEngine.putRecord(journalRecordId(repoDid, journal.id), journal.toRepoJson()).getOrThrow()
        val metadata =
            metadataStore.upsert(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.JOURNAL,
                recordKey = journal.id,
            )
        return journal.copy(version = metadata.version, lastUpdated = System.currentTimeMillis())
    }

    override suspend fun getJournal(
        userId: UUID,
        id: String,
    ): LogDateJournal? {
        val metadata = metadataStore.metadata(userId, LogDateCollectionKind.JOURNAL, id) ?: return null
        val repoDid = canonicalRepoDid(userId)
        return repoEngine
            .getRecord(journalRecordId(repoDid, id))
            .getOrThrow()
            ?.value
            ?.toLogDateJournal(recordKey = RecordKey.require(id), version = metadata.version)
            ?: missingRecord(
                userId = userId,
                collection = LogDateCollectionKind.JOURNAL,
                recordKey = id,
            )
    }

    override suspend fun deleteJournal(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        val existing = metadataStore.metadata(userId, LogDateCollectionKind.JOURNAL, id) ?: return
        val repoDid = canonicalRepoDid(userId)
        val deleted = repoEngine.deleteRecord(journalRecordId(repoDid, id)).getOrThrow()
        if (!deleted) {
            Napier.w("Expected canonical repo journal $id for user $userId before tombstoning it")
        }
        metadataStore.delete(
            userId = userId,
            repoDid = repoDid,
            collection = LogDateCollectionKind.JOURNAL,
            recordKey = existing.recordKey,
            deletedAt = deletedAt,
        )
    }

    override suspend fun recordJournalMergeDeletion(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ): Long {
        val repoDid = canonicalRepoDid(userId)
        return checkNotNull(
            metadataStore.delete(
                userId = userId,
                repoDid = repoDid,
                collection = LogDateCollectionKind.JOURNAL,
                recordKey = id,
                deletedAt = deletedAt,
                allowMissing = true,
            ),
        ).version
    }

    override suspend fun journalChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateJournal, LogDateJournalDeletion> {
        val repoDid = canonicalRepoDid(userId)
        val metadata = metadataStore.changes(userId, LogDateCollectionKind.JOURNAL, since, limit)
        return LogDateChangeSet(
            changes =
                repoEngine
                    .getRecords(metadata.changes.map { journalRecordId(repoDid, it.recordKey) })
                    .getOrThrow()
                    .zip(metadata.changes)
                    .mapNotNull { (record, change) ->
                        record
                            ?.value
                            ?.toLogDateJournal(recordKey = RecordKey.require(change.recordKey), version = change.version)
                            ?: missingRecord(userId, LogDateCollectionKind.JOURNAL, change.recordKey)
                    },
            deletions =
                metadata.deletions.map { deletion ->
                    LogDateJournalDeletion(
                        id = deletion.recordKey,
                        deletedAt = requireNotNull(deletion.deletedAt),
                        serverVersion = deletion.version,
                    )
                },
            lastTimestamp = metadata.lastTimestamp,
            hasMore = metadata.hasMore,
        )
    }

    override suspend fun listAssociations(userId: UUID): List<LogDateAssociation> = repoListAssociations(userId)

    override suspend fun upsertAssociations(
        userId: UUID,
        associations: List<LogDateAssociation>,
    ): List<LogDateAssociation> = repoUpsertAssociations(userId, associations)

    override suspend fun deleteAssociations(
        userId: UUID,
        associations: List<LogDateAssociationRef>,
        deletedAt: Long,
    ) = repoDeleteAssociations(userId, associations, deletedAt)

    override suspend fun associationChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateAssociation, LogDateAssociationDeletion> = repoAssociationChanges(userId, since, limit)

    override suspend fun upsertDraft(
        userId: UUID,
        draft: LogDateDraft,
    ): LogDateDraft = repoUpsertDraft(userId, draft)

    override suspend fun getDraft(
        userId: UUID,
        id: String,
    ): LogDateDraft? = repoGetDraft(userId, id)

    override suspend fun deleteDraft(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) = repoDeleteDraft(userId, id, deletedAt)

    override suspend fun draftChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateDraft, LogDateDraftDeletion> = repoDraftChanges(userId, since, limit)

    override suspend fun purgeTombstones(olderThan: Long): LogDateCollectionsPurgeResult = metadataStore.purgeAllTombstones(olderThan)

    override suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult = metadataStore.purgeTombstones(userId = userId, olderThan = olderThan)

    internal suspend fun canonicalRepoDid(userId: UUID): AtprotoDid = metadataStore.state(userId)?.repoDid ?: preferredRepoDid(userId)

    private suspend fun preferredRepoDid(userId: UUID): AtprotoDid {
        val account = accountRepository.findById(userId.toKotlinUuid())
        val ensuredDid = account?.let { identityService.ensureIdentity(it).did }
        return AtprotoDid.require(ensuredDid ?: syntheticRepoDid(userId))
    }

    internal suspend fun <T> listLiveRecords(
        userId: UUID,
        collection: LogDateCollectionKind,
        decode: suspend (AtprotoDid, LogDateCollectionMetadata) -> T?,
    ): List<T> {
        val repoDid = canonicalRepoDid(userId)
        return metadataStore.listLive(userId, collection).mapNotNull { metadata ->
            decode(repoDid, metadata) ?: missingRecord(userId, collection, metadata.recordKey)
        }
    }

    internal fun <T> missingRecord(
        userId: UUID,
        collection: LogDateCollectionKind,
        recordKey: String,
    ): T? {
        Napier.w("Missing canonical repo record for ${collection.storageName}:$recordKey on user $userId")
        return null
    }

    private fun syntheticRepoDid(userId: UUID): String = "did:web:sync-${userId.toString().replace("-", "")}.logdate.internal"

    internal fun repoRecordIdForAssociation(
        repoDid: AtprotoDid,
        recordKey: String,
    ) = RepoRecordId(
        repo = repoDid,
        collection = LogDateCollectionKind.ASSOCIATION.nsid,
        recordKey = RecordKey.require(recordKey),
    )
}
