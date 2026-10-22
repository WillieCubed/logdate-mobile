package app.logdate.client.feature.widgets

import android.app.Activity
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobWorkItem
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.ContextCompat
import app.logdate.client.device.restore.PostRestoreDetector
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val WIDGET_LIFECYCLE_JOB = 0x57494447

internal fun scheduleWidgetLifecycle(
    context: Context,
    retainedEvent: WidgetLifecycleEvent? = null,
): Boolean =
    try {
        val payload = retainedEvent?.let { Json.encodeToString(it) }
        val work =
            if (Build.VERSION.SDK_INT >= 34) {
                JobWorkItem.Builder().setExtras(PersistableBundle().apply { payload?.let { putString("event", it) } }).build()
            } else {
                JobWorkItem(Intent().apply { payload?.let { putExtra("event", it) } })
            }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        enqueueWidgetLifecycleWork(
            canExpedite = Build.VERSION.SDK_INT >= 31,
            pending = { scheduler.getPendingJob(WIDGET_LIFECYCLE_JOB) },
            create = { expedited ->
                JobInfo
                    .Builder(WIDGET_LIFECYCLE_JOB, ComponentName(context, WidgetLifecycleJobService::class.java))
                    .setPersisted(Build.VERSION.SDK_INT >= 34)
                    .apply {
                        if (expedited && Build.VERSION.SDK_INT >= 31) setExpedited(true) else setOverrideDeadline(0)
                    }.build()
            },
            enqueue = { scheduler.enqueue(it, work) == JobScheduler.RESULT_SUCCESS },
        )
        true
    } catch (error: Exception) {
        Napier.w("Widget lifecycle scheduling remains pending in its durable inbox", error)
        false
    }

class WidgetLifecycleJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    private var active: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        active = params
        running =
            scope.launch {
                try {
                    val inbox by lazy { widgetLifecycleInbox(this@WidgetLifecycleJobService) }

                    suspend fun reconcileInbox() {
                        val settings = WidgetInstanceSettings(this@WidgetLifecycleJobService)
                        check(!settings.isRecoveryPending)
                        inbox.pending().forEach { record ->
                            check(record.settingsApplied)
                            completeWidgetLifecycle(
                                persistRefresh = {
                                    withTimeout(
                                        30_000,
                                    ) { persistWidgetRefresh(this@WidgetLifecycleJobService, record.event) }
                                },
                                dispatch = { deliverWidgetLifecycle(record.event) },
                                acknowledge = { inbox.acknowledge(record.event.id) },
                            )
                        }
                    }
                    runWidgetLifecycleQueue(
                        prepare = {
                            check(GlobalContext.getOrNull() != null) { "The restricted restore process cannot run widget services" }
                            check(PostRestoreDetector(this@WidgetLifecycleJobService).isNativeRestoreDeliveryComplete())
                            reconcileInbox()
                        },
                        dequeue = params::dequeueWork,
                        process = { work ->
                            val payload =
                                if (Build.VERSION.SDK_INT >=
                                    34
                                ) {
                                    work.extras.getString("event")
                                } else {
                                    work.intent?.getStringExtra("event")
                                }
                            payload?.let { inbox.append(Json.decodeFromString<WidgetLifecycleEvent>(it)) }
                            reconcileInbox()
                        },
                        complete = params::completeWork,
                        retry = {
                            withContext(NonCancellable + Dispatchers.Main) {
                                if (active === params) jobFinished(params, true)
                            }
                        },
                        failed = { Napier.w("Widget lifecycle recovery will retry", it) },
                    )
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        if (active === params) {
                            active = null
                            running = null
                        }
                    }
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (active === params) {
            active = null
            running?.cancel()
            running = null
        }
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun deliverWidgetLifecycle(event: WidgetLifecycleEvent) =
        withContext(Dispatchers.Main) {
            val receiver = widgetReceivers().single { it.javaClass.simpleName == event.receiver }
            val action = widgetRuntimeAction(this@WidgetLifecycleJobService, event.receiver)
            ContextCompat.registerReceiver(
                this@WidgetLifecycleJobService,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            try {
                withTimeout(25_000) {
                    suspendCancellableCoroutine { completion ->
                        val callback =
                            object : BroadcastReceiver() {
                                override fun onReceive(
                                    context: Context,
                                    intent: Intent,
                                ) {
                                    if (completion.isActive) {
                                        if (resultCode == Activity.RESULT_OK && resultData == event.id) {
                                            completion.resume(Unit)
                                        } else {
                                            completion.resumeWithException(IOException("The widget runtime did not acknowledge delivery"))
                                        }
                                    }
                                }
                            }
                        sendOrderedBroadcast(
                            Intent(action).setPackage(packageName).putExtra(WIDGET_EVENT_ID, event.id),
                            null,
                            callback,
                            Handler(Looper.getMainLooper()),
                            0,
                            null,
                            null,
                        )
                    }
                }
            } finally {
                unregisterReceiver(receiver)
            }
        }
}

class WidgetLifecycleRestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) resumeWidgetLifecycle(context)
    }
}
