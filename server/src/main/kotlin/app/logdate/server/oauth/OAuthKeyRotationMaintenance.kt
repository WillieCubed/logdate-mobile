package app.logdate.server.oauth

import io.github.aakira.napier.Napier
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.minutes

private val ROTATION_CHECK_INTERVAL = 5.minutes

/** Moves OAuth signing key rotation forward on a schedule. See [OAuthKeyService.rotateIfDue]. */
internal fun Application.installOAuthKeyRotation() {
    val keyService by inject<OAuthKeyService>()
    val job =
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                delay(ROTATION_CHECK_INTERVAL)
                try {
                    when (keyService.rotateIfDue()) {
                        OAuthKeyRotationStep.STAGED -> Napier.i("Staged the next OAuth signing key")
                        OAuthKeyRotationStep.PROMOTED -> Napier.i("Promoted the next OAuth signing key; the previous key now only verifies")
                        OAuthKeyRotationStep.RETIRED -> Napier.i("Deleted the retired OAuth signing key")
                        OAuthKeyRotationStep.NONE -> Unit
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    Napier.e("OAuth signing key rotation check failed", exception)
                }
            }
        }
    monitor.subscribe(ApplicationStopped) { job.cancel() }
}
