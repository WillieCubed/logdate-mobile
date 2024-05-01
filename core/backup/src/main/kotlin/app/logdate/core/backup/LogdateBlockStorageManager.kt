package app.logdate.core.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesResponse
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Manages the block storage for logdate backups.
 *
 * This requires the device to support Google Play Services.
 */
class LogdateBlockStorageManager @Inject constructor(
    context: Context,
) {
    internal val blockstoreClient = Blockstore.getClient(context)

    private companion object {
        const val TAG = "LogdateBlockStorageManager"

        const val STORE_KEY_DEFAULT_SERVER = "app.logdate.mobile.DEFAULT_SERVER"
        const val STORE_KEY_DID = "app.logdate.mobile.USER_DID"
    }

    suspend fun getCredentials() = suspendCoroutine {
        val requestedKeys = listOf(STORE_KEY_DEFAULT_SERVER, STORE_KEY_DID)

        val retrieveRequest = RetrieveBytesRequest.Builder()
            .setKeys(requestedKeys)
            .build()

        blockstoreClient.retrieveBytes(retrieveRequest)
            .addOnSuccessListener { result: RetrieveBytesResponse ->
                val blockstoreDataMap = result.blockstoreDataMap
                val values = listOf(blockstoreDataMap.map { entry ->
                    val key = entry.key
                    val valueBytes = entry.value.bytes
                    key to valueBytes
                })
                it.resumeWith(Result.success(values))
            }
            .addOnFailureListener { e: Exception ->
                Log.e(
                    TAG,
                    "Failed to store bytes",
                    e
                )
                it.resumeWithException(e)
            }
    }

    suspend fun deleteDid() {
        val requestedKeys = listOf(STORE_KEY_DEFAULT_SERVER, STORE_KEY_DID)
        val retrieveRequest = DeleteBytesRequest.Builder()
            .setKeys(requestedKeys)
            .build()
    }

    suspend fun saveCredentials() {

    }
}