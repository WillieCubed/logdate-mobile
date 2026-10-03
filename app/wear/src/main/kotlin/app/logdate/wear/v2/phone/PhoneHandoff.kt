package app.logdate.wear.v2.phone

import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Whether the phone accepted a link the watch sent it. */
enum class HandoffResult {
    SENT,
    FAILED,
}

/**
 * Opens links on the phone from the watch. [packageName] is the watch's own, which the phone app
 * shares.
 */
class PhoneHandoff(
    private val remoteActivityHelper: RemoteActivityHelper,
    private val packageName: String,
    private val ioDispatcher: CoroutineDispatcher,
    private val viewIntent: (String) -> Intent = ::phoneViewIntent,
) {
    /** Opens LogDate's Play listing on the phone [nodeId]. */
    suspend fun openAppListing(nodeId: String): HandoffResult = open("$PLAY_LISTING$packageName", nodeId)

    suspend fun open(
        link: String,
        nodeId: String,
    ): HandoffResult =
        try {
            withContext(ioDispatcher) {
                remoteActivityHelper.startRemoteActivity(viewIntent(link), nodeId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            HandoffResult.SENT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't open $link on the phone", e)
            HandoffResult.FAILED
        }

    private companion object {
        const val PLAY_LISTING = "market://details?id="
        const val TIMEOUT_SECONDS = 10L
    }
}

/** The only kind of intent `RemoteActivityHelper` sends: a browsable view of [link]. */
fun phoneViewIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(link))
