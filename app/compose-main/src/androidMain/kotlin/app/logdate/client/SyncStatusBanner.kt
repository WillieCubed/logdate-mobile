@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncStatus
import org.koin.compose.koinInject

/**
 * Persistent in-app status banner that surfaces sync failures. The banner is silent when sync is
 * healthy or has never run; it appears only when the user has something to react to — an auth
 * error (tap to sign in), a transient network failure with items still to upload, or a generic
 * pipeline error.
 *
 * Rationale: before this existed, sync failures were logged but invisible. A user who opted into
 * cloud had no way to know data wasn't making it off the device. The launch bar for cloud is
 * "never wonder whether your data made it" — this banner is the primary signal.
 */
@Composable
fun SyncStatusBanner(
    modifier: Modifier = Modifier,
    onSignInRequested: () -> Unit = {},
    onRetryRequested: () -> Unit = {},
    syncManager: SyncManager = koinInject(),
) {
    val status by syncManager.syncStatusFlow.collectAsStateWithLifecycle()
    val presentation = status.toBannerPresentation()

    AnimatedVisibility(
        visible = presentation != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        presentation?.let { (message, action) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when (action) {
                    BannerAction.SIGN_IN -> {
                        TextButton(onClick = onSignInRequested) { Text("Sign in") }
                    }
                    BannerAction.RETRY -> {
                        TextButton(onClick = onRetryRequested) { Text("Retry") }
                    }
                    BannerAction.NONE -> Unit
                }
            }
        }
    }
}

private enum class BannerAction { NONE, SIGN_IN, RETRY }

private fun SyncStatus.toBannerPresentation(): Pair<String, BannerAction>? {
    if (!isEnabled) return null
    val error = lastError
    if (isSyncing) return null
    if (!hasErrors && pendingUploads == 0) return null

    return when (error?.type) {
        SyncErrorType.AUTHENTICATION_ERROR ->
            "You're signed out of LogDate Cloud. Sign in to resume sync." to BannerAction.SIGN_IN
        SyncErrorType.NETWORK_ERROR ->
            "Can't reach LogDate Cloud. $pendingUploads item${plural(pendingUploads)} pending." to BannerAction.RETRY
        SyncErrorType.SERVER_ERROR ->
            "LogDate Cloud is having trouble. Will retry shortly." to BannerAction.RETRY
        SyncErrorType.STORAGE_ERROR ->
            "Storage limit reached. Tap to manage your plan." to BannerAction.RETRY
        SyncErrorType.CONFLICT_ERROR ->
            "Some items couldn't sync due to edit conflicts." to BannerAction.NONE
        SyncErrorType.UNKNOWN_ERROR, null -> {
            if (pendingUploads > 0) {
                "$pendingUploads item${plural(pendingUploads)} waiting to sync." to BannerAction.RETRY
            } else {
                "Sync had trouble. Will retry shortly." to BannerAction.RETRY
            }
        }
    }
}

private fun plural(count: Int): String = if (count == 1) "" else "s"
