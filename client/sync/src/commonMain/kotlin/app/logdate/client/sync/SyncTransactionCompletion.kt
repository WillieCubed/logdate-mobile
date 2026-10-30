package app.logdate.client.sync

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Defers durable-state cleanup even when a test or platform manager executes transactions directly. */
suspend fun <T> SyncTransactionManager.withTransactionCompletion(block: suspend () -> T): T =
    withSyncTransactionCompletion(this, { operation -> withTransaction(operation) }, block)

internal suspend fun <T> withSyncTransactionCompletion(
    owner: Any,
    transaction: suspend (suspend () -> T) -> T,
    block: suspend () -> T,
): T {
    val enclosing = currentCompletion(owner)
    val completion = TransactionCompletion(owner, coroutineContext[TransactionCompletion])
    val result =
        try {
            withContext(completion) { transaction(block) }
        } catch (failure: Throwable) {
            withContext(NonCancellable) { completion.take() }
            throw failure
        }
    val cleanups = completion.take()
    if (enclosing?.addAll(cleanups) == true) return result
    for (cleanup in cleanups) {
        try {
            cleanup()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Committed recovery cleanup remains pending: ${error::class.simpleName}")
        }
    }
    return result
}

internal suspend fun afterSyncTransactionCommit(
    owner: Any,
    block: suspend () -> Unit,
) {
    if (currentCompletion(owner)?.add(block) == true) return
    block()
}

private suspend fun currentCompletion(owner: Any): TransactionCompletion? {
    var current = coroutineContext[TransactionCompletion]
    while (current != null) {
        if (current.owner === owner && current.active()) return current
        current = current.parent
    }
    return null
}

private class TransactionCompletion(
    val owner: Any,
    val parent: TransactionCompletion?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionCompletion>

    private val mutex = Mutex()
    private var open = true
    private val actions = mutableListOf<suspend () -> Unit>()

    suspend fun active(): Boolean = mutex.withLock { open }

    suspend fun add(block: suspend () -> Unit): Boolean =
        mutex.withLock {
            if (!open) return@withLock false
            actions += block
            true
        }

    suspend fun addAll(blocks: List<suspend () -> Unit>): Boolean =
        mutex.withLock {
            if (!open) return@withLock false
            actions += blocks
            true
        }

    suspend fun take(): List<suspend () -> Unit> =
        mutex.withLock {
            open = false
            actions.toList().also { actions.clear() }
        }
}
