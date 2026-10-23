package app.logdate.client.e2e

import app.logdate.client.networking.DiscoveryFailure
import app.logdate.client.networking.ServerDiscoveryException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Retains sanitized discovery evidence without changing the fixture's deadline or retries. */
internal suspend fun <T> awaitNativeEnrollmentServer(
    timeoutMillis: Long = 30_000,
    discover: suspend () -> Result<T>,
): T {
    var lastFailure: Throwable? = null
    try {
        return withTimeout(timeoutMillis) {
            while (true) {
                val result = discover()
                val failure = result.exceptionOrNull()
                if (failure == null) return@withTimeout result.getOrThrow()
                lastFailure = failure
                if (failure !is ServerDiscoveryException || failure.kind == DiscoveryFailure.UNREADABLE) throw failure
                delay(250)
            }
            error("Unreachable")
        }
    } catch (timeout: TimeoutCancellationException) {
        val last = lastFailure
        val kind = (last as? ServerDiscoveryException)?.kind?.name ?: "none"
        val evidence =
            generateSequence(last) { it.cause }
                .take(4)
                .mapNotNull { it.message?.take(2048) }
                .joinToString(" ")
        val reason =
            Regex("ECONNREFUSED|ENETUNREACH|EHOSTUNREACH|ETIMEDOUT|ECONNRESET|CLEARTEXT|HTTP [0-9]{3}", RegexOption.IGNORE_CASE)
                .findAll(evidence)
                .take(8)
                .joinToString(",") { it.value.uppercase() }
                .ifEmpty { "unclassified" }
        val causeClass =
            last
                ?.cause
                ?.javaClass
                ?.name
                ?.take(192) ?: "none"
        timeout.addSuppressed(
            IllegalStateException(
                "Enrollment discovery deadline: last=${last?.javaClass?.name?.take(192) ?: "none"}; " +
                    "kind=$kind; cause=$causeClass; reason=$reason",
            ),
        )
        throw timeout
    }
}
