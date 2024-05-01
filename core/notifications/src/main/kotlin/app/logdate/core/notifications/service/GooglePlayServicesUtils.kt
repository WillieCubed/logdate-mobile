package app.logdate.core.notifications.service

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> Task<T>.toCoroutine(): T = suspendCoroutine { continuation ->
    addOnCompleteListener {
        if (it.isSuccessful) {
            continuation.resume(it.result)
        } else {
            continuation.resumeWithException(it.exception ?: Exception("Task failed"))
        }
    }
}