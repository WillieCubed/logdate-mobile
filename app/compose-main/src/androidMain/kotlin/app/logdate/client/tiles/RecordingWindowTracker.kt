package app.logdate.client.tiles

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import app.logdate.client.media.audio.AudioRecordingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Asks LogDate's main window to bring forward the window a voice recording is running in. */
const val EXTRA_SHOW_RECORDING = "app.logdate.extra.SHOW_RECORDING"

/**
 * Remembers which LogDate window a voice recording started in. That can be a separate editor window
 * rather than the main one, so the Record voice tile opens the main window with
 * [EXTRA_SHOW_RECORDING], and the main window, now in front, brings the recording's window forward.
 */
class RecordingWindowTracker(
    private val recorder: AudioRecordingManager,
) : Application.ActivityLifecycleCallbacks {
    private var resumedActivity: WeakReference<Activity>? = null

    @Volatile
    private var recordingTaskId: Int? = null
    private var job: Job? = null

    fun start(
        application: Application,
        scope: CoroutineScope,
    ) {
        if (job != null) return
        application.registerActivityLifecycleCallbacks(this)
        job =
            scope.launch {
                recorder.getRecordingStateFlow().distinctUntilChanged().collect { recording ->
                    // A recording starts from the window in front, so that window holds it.
                    recordingTaskId = if (recording) resumedActivity?.get()?.taskId else null
                }
            }
    }

    /**
     * Brings the window holding the running recording in front of [from]. Returns false when nothing
     * is recording, the recording is in [from]'s own window, or its window has closed.
     */
    fun bringRecordingWindowForward(from: Activity): Boolean {
        val taskId = recordingTaskId ?: return false
        if (taskId == from.taskId) return false
        val task =
            from
                .getSystemService(ActivityManager::class.java)
                ?.appTasks
                ?.firstOrNull { it.taskInfo?.taskId == taskId }
                ?: return false
        task.moveToFront()
        return true
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
