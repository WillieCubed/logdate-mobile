package app.logdate.client.data.restore

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.RetainedJournalWorkspace
import app.logdate.client.device.restore.retainedJournalWorkspace
import io.github.aakira.napier.Napier

private const val RETAINED_JOURNAL_RECONCILIATION_JOB = 0x4e4a5235

/** Local, durable reconciliation. It has no network requirement and never clears source custody. */
fun scheduleRetainedJournalReconciliation(context: Context): Boolean =
    try {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job =
            JobInfo
                .Builder(
                    RETAINED_JOURNAL_RECONCILIATION_JOB,
                    ComponentName(context, RetainedJournalReconciliationJobService::class.java),
                ).setPersisted(true)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
        scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    } catch (_: Exception) {
        Napier.w("Retained journal reconciliation remains pending")
        false
    }

/** Called by app startup as well as each staged native delivery. */
fun resumeRetainedJournalReconciliation(context: Context) {
    if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
    val workspace = retainedJournalWorkspace(context)
    if (runCatching { workspace.current()?.phase == RetainedJournalWorkspace.Phase.WORKING }.getOrDefault(false) ||
        NativeJournalReplayCustody(context).hasInputs()
    ) {
        scheduleRetainedJournalReconciliation(context)
    }
}
