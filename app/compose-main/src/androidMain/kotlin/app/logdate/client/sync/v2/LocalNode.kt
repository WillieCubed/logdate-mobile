package app.logdate.client.sync.v2

import com.google.android.gms.wearable.NodeClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** This phone's Data Layer node, or null, with the failure logged, when Play services cannot say. */
internal suspend fun NodeClient.localNodeIdOrNull(): String? =
    try {
        localNode.await().id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Napier.e("Could not read this phone's Data Layer node; watches are linked once it can", e)
        null
    }
