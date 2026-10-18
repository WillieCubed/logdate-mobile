package app.logdate.client.launch

import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.logdate.client.ApplicationStartupCoordinator
import app.logdate.client.database.NativeRestoreInProgressException
import app.logdate.client.restore.NativeMediaQuarantinePendingException
import app.logdate.ui.GenericLoadingScreen
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import studio.hypertext.logdate.R

/** Attaches a responsive loading surface before resolving any storage-backed dependency. */
internal fun FragmentActivity.whenApplicationReady(onReady: () -> Unit) {
    val startup = ApplicationStartupCoordinator.get(application)
    if (startup.state.value is ApplicationStartupState.Ready) {
        onReady()
        return
    }
    setContent {
        val state by startup.state.collectAsState()
        val failure = state as? ApplicationStartupState.Failed
        LogDateTheme {
            Surface {
                GenericLoadingScreen()
                if (failure != null) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.application_startup_failed_title)) },
                        text = {
                            Text(stringResource(applicationStartupFailureMessage(failure.cause).stringResource()))
                        },
                        confirmButton = {
                            TextButton(onClick = startup::start) {
                                Text(stringResource(R.string.application_startup_retry))
                            }
                        },
                    )
                }
            }
        }
    }
    lifecycleScope.launch {
        startup.state.first { it is ApplicationStartupState.Ready }
        onReady()
    }
}

internal enum class ApplicationStartupFailureMessage {
    GENERIC,
    NATIVE_RESTORE_PENDING,
    NATIVE_MEDIA_PENDING,
    STORAGE_FULL,
}

internal fun applicationStartupFailureMessage(cause: Throwable): ApplicationStartupFailureMessage =
    when {
        cause.hasNativeMediaQuarantinePending() && cause.hasStorageFullCause() -> ApplicationStartupFailureMessage.STORAGE_FULL
        cause.hasNativeMediaQuarantinePending() -> ApplicationStartupFailureMessage.NATIVE_MEDIA_PENDING
        cause.hasNativeRestoreInProgress() -> ApplicationStartupFailureMessage.NATIVE_RESTORE_PENDING
        else -> ApplicationStartupFailureMessage.GENERIC
    }

private fun ApplicationStartupFailureMessage.stringResource(): Int =
    when (this) {
        ApplicationStartupFailureMessage.GENERIC -> R.string.application_startup_failed_message
        ApplicationStartupFailureMessage.NATIVE_RESTORE_PENDING -> R.string.native_restore_startup_incomplete_message
        ApplicationStartupFailureMessage.NATIVE_MEDIA_PENDING -> R.string.native_media_quarantine_pending_message
        ApplicationStartupFailureMessage.STORAGE_FULL -> R.string.native_media_quarantine_storage_message
    }

private fun Throwable.hasNativeRestoreInProgress(): Boolean = causesAndSuppressed().any { it is NativeRestoreInProgressException }

private fun Throwable.hasNativeMediaQuarantinePending(): Boolean = causesAndSuppressed().any { it is NativeMediaQuarantinePendingException }

private fun Throwable.hasStorageFullCause(): Boolean =
    causesAndSuppressed().any { cause ->
        cause.javaClass.simpleName in setOf("SQLiteFullException", "SQLiteDiskIOException") ||
            cause.message
                ?.lowercase()
                ?.let { "no space left" in it || "disk is full" in it || "database or disk is full" in it } == true
    }

private fun Throwable.causesAndSuppressed(): Sequence<Throwable> =
    sequence {
        val pending = ArrayDeque<Throwable>()
        val visited = mutableSetOf<Throwable>()
        pending.addLast(this@causesAndSuppressed)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            yield(current)
            current.cause?.let(pending::addLast)
            current.suppressed.forEach(pending::addLast)
        }
    }
