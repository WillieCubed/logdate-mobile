package app.logdate.client.device.restore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as withLifecycleLock

/** Coordinates BackupAgent callbacks with the worker that reconciles restored media identities. */
internal object AndroidNativeRestoreDeliveryGate : NativeRestoreDeliveryGate {
    private val mutex = Mutex()
    private val completedDeliveries = MutableStateFlow(0L)
    val completions: StateFlow<Long> = completedDeliveries.asStateFlow()
    private val lifecycleLock = ReentrantLock()
    private val callbacksIdle = lifecycleLock.newCondition()
    private var restoreOwner: Any? = null
    private var activeCallbacks = 0
    private var completionStarted = false

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun beginRestoreCallback() =
        lifecycleLock.withLifecycleLock {
            while (completionStarted) callbacksIdle.awaitUninterruptibly()
            ensureRestoreSession()
            activeCallbacks++
        }

    fun endRestoreCallback() =
        lifecycleLock.withLifecycleLock {
            check(activeCallbacks > 0) { "Native restore callback ended without starting" }
            activeCallbacks--
            if (activeCallbacks == 0) callbacksIdle.signalAll()
        }

    fun beginRestoreCompletion() =
        lifecycleLock.withLifecycleLock {
            while (completionStarted) callbacksIdle.awaitUninterruptibly()
            ensureRestoreSession()
            completionStarted = true
            while (activeCallbacks > 0) callbacksIdle.awaitUninterruptibly()
        }

    fun endRestore() =
        lifecycleLock.withLifecycleLock {
            while (activeCallbacks > 0) callbacksIdle.awaitUninterruptibly()
            val owner = restoreOwner ?: return@withLifecycleLock
            restoreOwner = null
            completionStarted = false
            runBlocking { mutex.unlock(owner) }
            completedDeliveries.value = completedDeliveries.value + 1
            callbacksIdle.signalAll()
        }

    private fun ensureRestoreSession() {
        if (restoreOwner != null) return
        val owner = Any()
        runBlocking { mutex.lock(owner) }
        restoreOwner = owner
    }
}

/** An ended callback batch is a retry hint; database, key and privacy guards remain authoritative. */
fun nativeRestoreDeliveryCompletions(): StateFlow<Long> = AndroidNativeRestoreDeliveryGate.completions
