package app.logdate.server.accountkeys

import java.util.UUID

interface AccountKeyEnvelopeRepository {
    suspend fun get(
        accountId: UUID,
        credentialId: String,
    ): String?

    /** Accept identical retries; never replace a credential's existing envelope. */
    suspend fun put(
        accountId: UUID,
        credentialId: String,
        ciphertext: String,
    ): Boolean
}

class InMemoryAccountKeyEnvelopeRepository : AccountKeyEnvelopeRepository {
    private val envelopes = mutableMapOf<Pair<UUID, String>, String>()

    override suspend fun get(
        accountId: UUID,
        credentialId: String,
    ): String? = synchronized(envelopes) { envelopes[accountId to credentialId] }

    override suspend fun put(
        accountId: UUID,
        credentialId: String,
        ciphertext: String,
    ): Boolean =
        synchronized(envelopes) {
            val key = accountId to credentialId
            val existing = envelopes[key]
            if (existing == null) envelopes[key] = ciphertext
            existing == null || existing == ciphertext
        }
}
