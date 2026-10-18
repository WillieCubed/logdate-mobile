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
                            Text(
                                stringResource(
                                    if (failure.cause.hasNativeRestoreInProgress()) {
                                        R.string.native_restore_startup_incomplete_message
                                    } else {
                                        R.string.application_startup_failed_message
                                    },
                                ),
                            )
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

private fun Throwable.hasNativeRestoreInProgress(): Boolean =
    generateSequence(this) { it.cause }.any { it is NativeRestoreInProgressException }
