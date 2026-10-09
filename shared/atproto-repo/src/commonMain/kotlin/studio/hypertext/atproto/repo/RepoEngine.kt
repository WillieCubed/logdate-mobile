package studio.hypertext.atproto.repo

import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Tid

/**
 * Canonical repo engine built on deterministic blocks and commit history.
 */
public interface RepoEngine : RepoRecordStore {
    /**
     * Loads the current repo head.
     */
    public suspend fun loadHead(repo: AtprotoDid): Result<RepoHead?>

    /**
     * Lists commits for [repo].
     */
    public suspend fun listCommits(
        repo: AtprotoDid,
        limit: Int = DEFAULT_COMMIT_LIMIT,
    ): Result<List<SignedRepoCommit>>

    /**
     * Exports [repo] into a CAR archive payload.
     */
    public suspend fun export(
        repo: AtprotoDid,
        since: Tid? = null,
    ): Result<RepoExport>

    /**
     * Imports [export] and installs it as the current repo state.
     */
    public suspend fun import(export: RepoExport): Result<RepoHead>

    public companion object {
        /**
         * Default commit history page size.
         */
        public const val DEFAULT_COMMIT_LIMIT: Int = 50
    }
}

/**
 * Raised when the repo head moved while this write was building its new tree.
 */
public class RepoHeadConflictException(
    public val repo: AtprotoDid,
    public val expectedRevision: Long?,
) : RepoException("Repo $repo moved past revision $expectedRevision while this write was in flight")

/**
 * Raised when compare-and-swap metadata does not match the current record state.
 */
public class InvalidSwapException(
    public val expectedCid: String?,
    public val providedCid: String,
) : RepoException("Invalid swapRecord")
