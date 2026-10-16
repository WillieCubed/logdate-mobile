package app.logdate.client.data.account

import app.logdate.shared.model.CompleteAuthenticationData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Acknowledges only an accepted account whose session has reached durable secure storage. */
internal class BackgroundRestoreCredentialRecovery(
    private val policy: NativeRestoreAccountPolicy,
    private val exchange: suspend () -> Result<CompleteAuthenticationData>,
    private val persist: suspend (CompleteAuthenticationData) -> Unit,
    private val acknowledge: () -> Unit,
) {
    suspend operator fun invoke(): Boolean {
        if (!policy.canAttempt) return false
        return try {
            val authenticated = exchange().getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!policy.accepts(authenticated.account.id.toString())) return false
            persist(authenticated)
            currentCoroutineContext().ensureActive()
            acknowledge()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            Napier.w("Background native account recovery remains pending")
            false
        }
    }
}
