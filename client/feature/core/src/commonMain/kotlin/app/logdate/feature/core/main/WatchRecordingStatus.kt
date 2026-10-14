@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.client.sync.continuity.WatchLiveSessionStore
import app.logdate.client.sync.continuity.WatchRecordingCompanion
import app.logdate.client.sync.datalayer.v2.RecordingPhase
import app.logdate.ui.audio.RecordingSessionControl
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.recording_session_watch_unreachable
import logdate.client.ui.generated.resources.recording_source_watch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

@Composable
internal fun WatchRecordingStatus() {
    val koin = getKoin()
    val companion = remember(koin) { koin.getOrNull<WatchRecordingCompanion>() } ?: return
    var session by remember { mutableStateOf<WatchLiveSessionStore.Session?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(companion) {
        while (true) {
            session = companion.current()
            if (session == null) failed = false
            delay(1_000)
        }
    }
    val current = session ?: return
    LaunchedEffect(current.state.sessionId) { failed = false }
    WatchRecordingContent(current.elapsedMs, stopping, failed, current.state.phase == RecordingPhase.PAUSED) {
        scope.launch {
            stopping = true
            val accepted = companion.stop()
            if (companion.current()?.state?.sessionId == current.state.sessionId) failed = !accepted
            stopping = false
        }
    }
}

@Composable
fun WatchRecordingContent(
    elapsedMs: Long,
    stopping: Boolean,
    failed: Boolean,
    paused: Boolean = false,
    onStop: () -> Unit,
) {
    RecordingSessionControl(
        source = stringResource(Res.string.recording_source_watch),
        elapsedMs = elapsedMs,
        paused = paused,
        stopping = stopping,
        error = if (failed) stringResource(Res.string.recording_session_watch_unreachable) else null,
        onStop = onStop,
        modifier = Modifier.padding(bottom = Spacing.md),
    )
}
