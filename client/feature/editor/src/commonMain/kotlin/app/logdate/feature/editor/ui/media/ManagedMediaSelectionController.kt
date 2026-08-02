package app.logdate.feature.editor.ui.media

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

sealed interface ManagedMediaSelectionState {
    data object Idle : ManagedMediaSelectionState

    data object Importing : ManagedMediaSelectionState

    data object Failed : ManagedMediaSelectionState
}

internal class ManagedMediaSelectionController(
    private val discardManagedMedia: suspend (String) -> Unit = {},
    private val importMedia: suspend (String) -> String,
) {
    private val mutableState = MutableStateFlow<ManagedMediaSelectionState>(ManagedMediaSelectionState.Idle)
    private val importMutex = Mutex()
    private var failedSourceUri: String? = null

    val state: StateFlow<ManagedMediaSelectionState> = mutableState.asStateFlow()

    suspend fun select(sourceUri: String): String? = selectPrepared(sourceUri) { it }

    suspend fun <T> selectPrepared(
        sourceUri: String,
        prepareManagedMedia: suspend (String) -> T,
    ): T? = tryWithAdmission { selectPreparedLocked(sourceUri, prepareManagedMedia) }

    suspend fun <T> selectPreparedAndTransfer(
        sourceUri: String,
        prepareManagedMedia: suspend (String) -> T,
        transferOwnership: (T) -> Unit,
    ) {
        tryWithAdmission {
            selectPreparedLocked(sourceUri, prepareManagedMedia, transferOwnership)
        }
    }

    suspend fun retry(): String? = retryPrepared { it }

    suspend fun <T> retryPrepared(prepareManagedMedia: suspend (String) -> T): T? =
        tryWithAdmission {
            val sourceUri = failedSourceUri ?: return@tryWithAdmission null
            selectPreparedLocked(sourceUri, prepareManagedMedia)
        }

    suspend fun <T> retryPreparedAndTransfer(
        prepareManagedMedia: suspend (String) -> T,
        transferOwnership: (T) -> Unit,
    ) {
        tryWithAdmission {
            val sourceUri = failedSourceUri ?: return@tryWithAdmission
            selectPreparedLocked(sourceUri, prepareManagedMedia, transferOwnership)
        }
    }

    suspend fun cancel() {
        tryWithAdmission {
            failedSourceUri = null
            mutableState.value = ManagedMediaSelectionState.Idle
        }
    }

    private suspend fun <T> tryWithAdmission(block: suspend () -> T): T? {
        if (!importMutex.tryLock()) return null

        return try {
            block()
        } finally {
            importMutex.unlock()
        }
    }

    private suspend fun <T> selectPreparedLocked(
        sourceUri: String,
        prepareManagedMedia: suspend (String) -> T,
        transferOwnership: ((T) -> Unit)? = null,
    ): T? {
        failedSourceUri = sourceUri
        mutableState.value = ManagedMediaSelectionState.Importing
        var ownedManagedUri: String? = null
        var ownershipTransferred = false

        try {
            val managedUri = importMedia(sourceUri)
            check(managedUri.isNotBlank()) { "Managed media URI cannot be blank" }
            check(managedUri != sourceUri) { "Managed media import returned the transient source URI" }
            ownedManagedUri = managedUri
            currentCoroutineContext().ensureActive()

            val preparedMedia = prepareManagedMedia(managedUri)
            currentCoroutineContext().ensureActive()

            transferOwnership?.invoke(preparedMedia)
            ownershipTransferred = true
            failedSourceUri = null
            mutableState.value = ManagedMediaSelectionState.Idle
            return preparedMedia
        } catch (cancellation: CancellationException) {
            if (!ownershipTransferred) {
                discardOwnedMedia(ownedManagedUri, cancellation)
            }
            failedSourceUri = null
            mutableState.value = ManagedMediaSelectionState.Idle
            throw cancellation
        } catch (error: Exception) {
            if (ownershipTransferred) {
                mutableState.value = ManagedMediaSelectionState.Idle
                throw error
            }

            discardOwnedMedia(ownedManagedUri, error)
            Napier.e("Failed to import or prepare selected media", error)
            mutableState.value = ManagedMediaSelectionState.Failed
            return null
        } finally {
            if (ownershipTransferred) {
                mutableState.value = ManagedMediaSelectionState.Idle
            }
        }
    }

    private suspend fun discardOwnedMedia(
        managedUri: String?,
        primaryFailure: Throwable,
    ) {
        if (managedUri == null) return

        try {
            withContext(NonCancellable) {
                discardManagedMedia(managedUri)
            }
        } catch (cleanupFailure: Throwable) {
            primaryFailure.addSuppressed(cleanupFailure)
            Napier.e("Failed to discard unowned managed media", cleanupFailure)
        }
    }
}
