package studio.hypertext.atproto.pds.runtime

import studio.hypertext.atproto.pds.GetLatestCommitRequest
import studio.hypertext.atproto.pds.GetLatestCommitResponse
import studio.hypertext.atproto.pds.GetRepoRequest
import studio.hypertext.atproto.pds.GetRepoStatusRequest
import studio.hypertext.atproto.pds.GetRepoStatusResponse
import studio.hypertext.atproto.pds.PdsSyncService
import studio.hypertext.atproto.pds.RepoExportResponse
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.RepoEngine
import studio.hypertext.atproto.syntax.Tid

/**
 * Default sync/export service implementation backed by a [RepoEngine].
 */
public class DefaultPdsSyncService(
    private val repoEngine: RepoEngine,
) : PdsSyncService {
    override suspend fun getRepo(request: GetRepoRequest): Result<RepoExportResponse?> =
        repoEngine.export(request.did, request.since).mapCatching { export ->
            RepoExportResponse(
                contentType = CAR_CONTENT_TYPE,
                bytes = CarCodec.write(export),
            )
        }

    override suspend fun getLatestCommit(request: GetLatestCommitRequest): Result<GetLatestCommitResponse?> =
        repoEngine.loadHead(request.did).mapCatching { head ->
            head?.let {
                GetLatestCommitResponse(
                    cid = it.commitCid,
                    rev = Tid.fromLong(it.revision),
                )
            }
        }

    override suspend fun getRepoStatus(request: GetRepoStatusRequest): Result<GetRepoStatusResponse?> =
        repoEngine.loadHead(request.did).mapCatching { head ->
            head?.let {
                GetRepoStatusResponse(
                    did = request.did,
                    active = true,
                    rev = Tid.fromLong(it.revision),
                )
            }
        }

    public companion object {
        /**
         * Standard CAR content type for repository exports.
         */
        public const val CAR_CONTENT_TYPE: String = "application/vnd.ipld.car"
    }
}
