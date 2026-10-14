@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import app.logdate.client.sync.continuity.HandoffFailure
import app.logdate.client.sync.continuity.WatchPlaybackCompanion
import app.logdate.ui.audio.PlaybackHandoffFailure
import app.logdate.ui.audio.PlaybackHandoffState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.getKoin

@Composable
fun rememberWatchPlaybackHandoff(): PlaybackHandoffState {
    val koin = getKoin()
    val companion = remember(koin) { koin.getOrNull<WatchPlaybackCompanion>() }
    val manager = remember(koin) { koin.getOrNull<AudioPlaybackManager>() }
    val playback = (manager as? AudioPlaybackStatusProvider)?.playbackStatus?.collectAsState()
    val noteId = playback?.value?.metadata?.noteId
    var available by remember(companion) { mutableStateOf(false) }
    var busy by remember(companion) { mutableStateOf(false) }
    var failed by remember(companion) { mutableStateOf(false) }
    var completed by remember(companion) { mutableStateOf(false) }
    var noteNotOnWatch by remember(companion) { mutableStateOf(false) }
    var failure by remember(companion) { mutableStateOf<PlaybackHandoffFailure?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(noteId) {
        failed = false
        noteNotOnWatch = false
        failure = null
        completed = false
    }
    LaunchedEffect(companion) {
        while (companion != null) {
            available = companion.available()
            if (available) completed = false
            delay(2_000)
        }
    }
    return PlaybackHandoffState(available, busy, failed, completed, noteNotOnWatch, failure) {
        if (busy || companion == null) return@PlaybackHandoffState
        val requestedNote = noteId
        scope.launch {
            busy = true
            failed = false
            val accepted = companion.continueOnWatch()
            if ((manager as? AudioPlaybackStatusProvider)
                    ?.playbackStatus
                    ?.value
                    ?.metadata
                    ?.noteId == requestedNote
            ) {
                completed = accepted
                failed = !accepted
                noteNotOnWatch = companion.lastFailure == HandoffFailure.AUDIO_NOT_ON_WATCH
                failure =
                    when (companion.lastFailure) {
                        HandoffFailure.WATCH_STARTING -> PlaybackHandoffFailure.WATCH_STARTING
                        HandoffFailure.WATCH_DID_NOT_RESPOND -> PlaybackHandoffFailure.WATCH_UNREACHABLE
                        HandoffFailure.WATCH_DID_NOT_OPEN -> PlaybackHandoffFailure.WATCH_NOT_OPENED
                        HandoffFailure.NOT_ALLOWED -> PlaybackHandoffFailure.WATCH_SETUP
                        HandoffFailure.PLAYBACK_FAILED -> PlaybackHandoffFailure.WATCH_PLAYBACK
                        HandoffFailure.PHONE_PLAYBACK_CHANGED -> PlaybackHandoffFailure.PHONE_CHANGED
                        else -> null
                    }
            }
            busy = false
        }
    }
}
