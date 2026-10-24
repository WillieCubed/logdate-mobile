package app.logdate.client.media.storage

/** Durable candidates to reconsider after a media claim changes. Recording is safe before mutation. */
fun interface MediaReclamationJournal {
    suspend fun recordCandidate(reference: String)

    object None : MediaReclamationJournal {
        override suspend fun recordCandidate(reference: String) = Unit
    }
}
