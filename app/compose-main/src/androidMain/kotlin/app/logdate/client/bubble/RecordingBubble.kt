package app.logdate.client.bubble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.logdate.client.MainActivity
import app.logdate.client.capture.ACTION_CAPTURE
import app.logdate.client.capture.EXTRA_CAPTURE_MODE
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.AndroidAudioNotificationHandler
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioRecordingService
import app.logdate.client.notifications.ACTION_FLOAT_RECORDER
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.notifications.RecordingBubbleSwitch
import app.logdate.feature.editor.ui.audio.RecorderBubbleContent
import app.logdate.feature.editor.ui.audio.RecorderBubbleState
import app.logdate.feature.editor.ui.audio.recorderBubbleStates
import app.logdate.ui.theme.LogDateTheme
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import studio.hypertext.logdate.R
import kotlin.time.Duration
import app.logdate.client.feature.widgets.R as WidgetResources

/** Height Android should give the expanded bubble, in dp. */
private const val BUBBLE_HEIGHT_DP = 480

/**
 * The window inside the floating recorder bubble. It shows the running recording and controls it
 * through the same service actions as the recording notification, so both always agree.
 *
 * Declared disabled in the manifest; `PlatformComponentToggler` enables it while the
 * RECORDING_BUBBLE flag is on.
 */
class RecordingBubbleActivity :
    ComponentActivity(),
    KoinComponent {
    private val recorder: AudioRecordingManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogDateTheme {
                val states = remember { recorder.bubbleStates() }
                val state by states.collectAsState(initial = recorder.initialBubbleState())
                RecorderBubbleContent(
                    state = state,
                    onPauseToggle = {
                        val action =
                            if (state.paused) {
                                AndroidAudioNotificationHandler.ACTION_RESUME
                            } else {
                                AndroidAudioNotificationHandler.ACTION_PAUSE
                            }
                        sendRecorderAction(action)
                    },
                    onFinish = { sendRecorderAction(AudioRecordingService.SERVICE_ACTION_STOP) },
                    onOpenApp = ::openApp,
                )
            }
        }
    }

    private fun sendRecorderAction(action: String) {
        runCatching { startService(Intent(this, AudioRecordingService::class.java).setAction(action)) }
            .onFailure { error -> Napier.e("Recorder bubble could not send $action", error) }
    }

    private fun openApp() {
        val intent =
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: return
        startActivity(intent)
    }
}

/** Receives the recording notification's Float action and floats the recorder. */
class FloatRecorderReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val notifier: RecordingBubbleNotifier by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_FLOAT_RECORDER) return
        notifier.float()
    }
}

/**
 * Posts and removes the floating recorder bubble.
 *
 * Android only floats conversation notifications, so the bubble is a `MessagingStyle` notification
 * tied to a long-lived "Voice recorder" shortcut. It is separate from the recording service's own
 * notification, which has to stay a plain ongoing notification to qualify as a Live Update. Where
 * the person has not allowed bubbles, the same notification appears in the shade with the system's
 * bubble button.
 */
class RecordingBubbleNotifier(
    private val context: Context,
    private val recorder: AudioRecordingManager,
) {
    private var job: Job? = null

    /** Removes the bubble whenever a recording ends, however it ended. */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job =
            scope.launch {
                recorder
                    .getRecordingStateFlow()
                    .distinctUntilChanged()
                    .filter { recording -> !recording }
                    .collect { dismiss() }
            }
    }

    /** Floats the recorder for the running recording. Does nothing when nothing is recording. */
    @SuppressLint("MissingPermission")
    fun float() {
        if (!recorder.isRecording) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) {
            Napier.w("Recorder bubble not shown: notifications are off for LogDate")
            return
        }
        LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.FLOATING_RECORDER)

        val title = context.getString(R.string.recorder_bubble_title)
        val icon = IconCompat.createWithResource(context, WidgetResources.drawable.ic_shortcut_record_voice)
        val recorderPerson =
            Person
                .Builder()
                .setName(title)
                .setIcon(icon)
                .setKey(SHORTCUT_ID)
                .setImportant(true)
                .build()
        ShortcutManagerCompat.pushDynamicShortcut(
            context,
            ShortcutInfoCompat
                .Builder(context, SHORTCUT_ID)
                .setShortLabel(title)
                .setIcon(icon)
                .setLongLived(true)
                .setPerson(recorderPerson)
                .setLocusId(LocusIdCompat(SHORTCUT_ID))
                .setIntent(
                    Intent(ACTION_CAPTURE)
                        .setClass(context, MainActivity::class.java)
                        .putExtra(EXTRA_CAPTURE_MODE, "voice"),
                ).build(),
        )

        val bubbleIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, RecordingBubbleActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        val bubble =
            NotificationCompat.BubbleMetadata
                .Builder(bubbleIntent, icon)
                .setDesiredHeight(BUBBLE_HEIGHT_DP)
                .setAutoExpandBubble(true)
                .setSuppressNotification(true)
                .build()
        val style =
            NotificationCompat
                .MessagingStyle(recorderPerson)
                .addMessage(context.getString(R.string.recorder_bubble_message), System.currentTimeMillis(), recorderPerson)
        val notification =
            NotificationCompat
                .Builder(context, LogDateNotificationChannelKey.FLOATING_RECORDER.id)
                .setSmallIcon(R.drawable.ic_tile_record_voice)
                .setContentTitle(title)
                .setContentIntent(bubbleIntent)
                .setShortcutId(SHORTCUT_ID)
                .setLocusId(LocusIdCompat(SHORTCUT_ID))
                .addPerson(recorderPerson)
                .setStyle(style)
                .setBubbleMetadata(bubble)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setSilent(true)
                .build()
        runCatching { notifications.notify(NOTIFICATION_ID, notification) }
            .onFailure { error -> Napier.e("Recorder bubble could not be posted", error) }
    }

    /** Removes the bubble and its shortcut. */
    fun dismiss() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID))
    }

    companion object {
        const val SHORTCUT_ID = "voice_recorder"
        val NOTIFICATION_ID = LogDateNotificationChannelKey.FLOATING_RECORDER.notificationId ?: 1005
    }
}

/** The [RecordingBubbleSwitch] the app provides: on while the RECORDING_BUBBLE feature flag is on. */
class FlagRecordingBubbleSwitch(
    flags: FeatureFlagStore,
    scope: CoroutineScope,
) : RecordingBubbleSwitch {
    private val state: StateFlow<Boolean> =
        flags.observe(FeatureFlag.RECORDING_BUBBLE).stateIn(scope, SharingStarted.Eagerly, FeatureFlag.RECORDING_BUBBLE.defaultEnabled)

    override val enabled: Boolean get() = state.value
}

private fun AudioRecordingManager.initialBubbleState() =
    RecorderBubbleState(elapsed = Duration.ZERO, levels = emptyList(), paused = currentRecordingPaused, transcript = null)

/** The recorder's live state as the bubble draws it. */
private fun AudioRecordingManager.bubbleStates(): Flow<RecorderBubbleState> =
    recorderBubbleStates(
        elapsed = getRecordingDurationFlow(),
        levels = getAudioLevelFlow(),
        paused = getRecordingPausedFlow(),
        transcript = getTranscriptionFlow(),
        initiallyPaused = currentRecordingPaused,
    )
