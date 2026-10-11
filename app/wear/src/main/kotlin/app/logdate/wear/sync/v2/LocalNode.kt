package app.logdate.wear.sync.v2

import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** This watch's Data Layer node, or null, with the failure logged, when Play services cannot say. */
internal suspend fun NodeClient.localNodeIdOrNull(): String? =
    try {
        localNode.await().id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Could not read this watch's Data Layer node; link requests are refused until it can", e)
        null
    }
