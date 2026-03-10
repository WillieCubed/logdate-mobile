package studio.hypertext.atproto.pds.runtime

import studio.hypertext.atproto.pds.CreateRecordRequest
import studio.hypertext.atproto.pds.DeleteRecordRequest
import studio.hypertext.atproto.pds.GetRecordRequest
import studio.hypertext.atproto.pds.ListRecordsRequest
import studio.hypertext.atproto.pds.ListRecordsResponse
import studio.hypertext.atproto.pds.PdsRepoService
import studio.hypertext.atproto.pds.PutRecordRequest
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoRecordStore
import studio.hypertext.atproto.repo.RepoWriteResult

/**
 * Default repo service implementation backed by a [RepoRecordStore].
 */
public class DefaultPdsRepoService(
    private val repoRecordStore: RepoRecordStore,
) : PdsRepoService {
    override suspend fun getRecord(request: GetRecordRequest): Result<RepoRecord?> =
        repoRecordStore
            .getRecord(
                studio.hypertext.atproto.repo.RepoRecordId(
                    repo = request.repo,
                    collection = request.collection,
                    recordKey = request.recordKey,
                ),
            ).mapCatching { record ->
                if (request.cid != null && record?.cid != request.cid) {
                    null
                } else {
                    record
                }
            }

    override suspend fun listRecords(request: ListRecordsRequest): Result<ListRecordsResponse> =
        repoRecordStore
            .listRecords(
                repo = request.repo,
                collection = request.collection,
                limit = request.limit,
                cursor = request.cursor,
                reverse = request.reverse,
            ).mapCatching(ListRecordsResponse::fromPage)

    override suspend fun createRecord(request: CreateRecordRequest): Result<RepoWriteResult> =
        repoRecordStore.createRecord(
            repo = request.repo,
            collection = request.collection,
            value = request.record,
            recordKey = request.recordKey,
        )

    override suspend fun putRecord(request: PutRecordRequest): Result<RepoWriteResult> =
        repoRecordStore.putRecord(
            recordId =
                studio.hypertext.atproto.repo.RepoRecordId(
                    repo = request.repo,
                    collection = request.collection,
                    recordKey = request.recordKey,
                ),
            value = request.record,
            swapRecord = request.swapRecord,
        )

    override suspend fun deleteRecord(request: DeleteRecordRequest): Result<Boolean> =
        repoRecordStore.deleteRecord(
            recordId =
                studio.hypertext.atproto.repo.RepoRecordId(
                    repo = request.repo,
                    collection = request.collection,
                    recordKey = request.recordKey,
                ),
            swapRecord = request.swapRecord,
        )
}
