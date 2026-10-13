package app.logdate.server.logdate

/** Commits a repo mutation and its sync index as one durable write. */
internal interface RepoWriteTransaction {
    suspend fun <T> write(action: suspend () -> T): T

    data object Direct : RepoWriteTransaction {
        override suspend fun <T> write(action: suspend () -> T): T = action()
    }
}
