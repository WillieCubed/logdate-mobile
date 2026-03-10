package studio.hypertext.atproto.repo

import studio.hypertext.atproto.identity.AtprotoDid

/**
 * Persistence contract for repo blocks, heads, and commit history.
 */
public interface RepoBlockStore {
    /**
     * Loads the current repo head.
     */
    public suspend fun readHead(repo: AtprotoDid): Result<RepoHead?>

    /**
     * Persists [head] as the current repo head.
     */
    public suspend fun writeHead(head: RepoHead): Result<Unit>

    /**
     * Reads a single block by [cid].
     */
    public suspend fun readBlock(cid: Cid): Result<RepoBlock?>

    /**
     * Persists [block] and associates it with [repo].
     */
    public suspend fun writeBlock(
        repo: AtprotoDid,
        block: RepoBlock,
    ): Result<Unit>

    /**
     * Clears [repo]'s head, block associations, and commit history.
     */
    public suspend fun clearRepo(repo: AtprotoDid): Result<Unit>

    /**
     * Lists blocks currently associated with [repo].
     */
    public suspend fun listBlocks(repo: AtprotoDid): Result<List<RepoBlock>>

    /**
     * Appends [commit] to [repo]'s commit history.
     */
    public suspend fun appendCommit(
        repo: AtprotoDid,
        commit: SignedRepoCommit,
    ): Result<Unit>

    /**
     * Lists commits for [repo] in oldest-to-newest order.
     */
    public suspend fun listCommits(repo: AtprotoDid): Result<List<SignedRepoCommit>>
}

/**
 * In-memory repo block store for tests and standalone use.
 */
public class InMemoryRepoBlockStore : RepoBlockStore {
    private val heads: MutableMap<AtprotoDid, RepoHead> = linkedMapOf()
    private val blocks: MutableMap<Cid, RepoBlock> = linkedMapOf()
    private val repoBlocks: MutableMap<AtprotoDid, LinkedHashSet<Cid>> = linkedMapOf()
    private val commits: MutableMap<AtprotoDid, MutableList<SignedRepoCommit>> = linkedMapOf()

    override suspend fun readHead(repo: AtprotoDid): Result<RepoHead?> = Result.success(heads[repo])

    override suspend fun writeHead(head: RepoHead): Result<Unit> =
        Result.success(
            run {
                heads[head.repo] = head
                Unit
            },
        )

    override suspend fun readBlock(cid: Cid): Result<RepoBlock?> = Result.success(blocks[cid])

    override suspend fun writeBlock(
        repo: AtprotoDid,
        block: RepoBlock,
    ): Result<Unit> =
        Result.success(
            run {
                blocks[block.cid] = block
                repoBlocks.getOrPut(repo) { linkedSetOf() }.add(block.cid)
                Unit
            },
        )

    override suspend fun clearRepo(repo: AtprotoDid): Result<Unit> =
        Result.success(
            run {
                heads.remove(repo)
                repoBlocks.remove(repo)
                commits.remove(repo)
                Unit
            },
        )

    override suspend fun listBlocks(repo: AtprotoDid): Result<List<RepoBlock>> =
        Result.success(
            repoBlocks[repo]
                .orEmpty()
                .mapNotNull(blocks::get),
        )

    override suspend fun appendCommit(
        repo: AtprotoDid,
        commit: SignedRepoCommit,
    ): Result<Unit> =
        Result.success(
            run {
                commits.getOrPut(repo) { mutableListOf() }.add(commit)
                Unit
            },
        )

    override suspend fun listCommits(repo: AtprotoDid): Result<List<SignedRepoCommit>> = Result.success(commits[repo].orEmpty().toList())
}
