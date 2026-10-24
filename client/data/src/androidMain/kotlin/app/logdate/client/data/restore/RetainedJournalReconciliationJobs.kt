package app.logdate.client.data.restore

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.openRetainedDatabaseCopy
import app.logdate.client.database.readRetainedDatabasePassphrases
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.RetainedJournalWorkspace
import app.logdate.client.device.restore.admitNativeContent
import app.logdate.client.device.restore.nativeRestoreDeliveryGate
import app.logdate.client.device.restore.retainedJournalWorkspace
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.uuid.Uuid

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
    if (runCatching { workspace.current()?.phase == RetainedJournalWorkspace.Phase.WORKING }.getOrDefault(false)) {
        scheduleRetainedJournalReconciliation(context)
    }
}

class RetainedJournalReconciliationJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Job? = null
    private var parameters: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        active?.cancel()
        parameters = params
        active =
            serviceScope.launch {
                var retry = false
                try {
                    retry = withTimeout(RECONCILIATION_TIMEOUT_MS) { reconcile() }
                } catch (_: TimeoutCancellationException) {
                    retry = true
                    Napier.w("Retained journal reconciliation reached its time limit and will continue")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    retry = true
                    Napier.w("Retained journal reconciliation will retry")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        if (parameters === params) {
                            parameters = null
                            active = null
                            jobFinished(params, retry)
                        }
                    }
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (parameters === params) {
            parameters = null
            active?.cancel()
            active = null
        }
        return true
    }

    override fun onDestroy() {
        active?.cancel()
        parameters = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun reconcile(): Boolean {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return true
        val koin = GlobalContext.getOrNull() ?: return true
        return RetainedJournalReconciliationLock.prepareThenWithDeliveryGate(
            nativeRestoreDeliveryGate(),
            prepare = {
                val expectedSession =
                    retainedJournalWorkspace(this)
                        .current()
                        ?.takeIf { it.phase == RetainedJournalWorkspace.Phase.WORKING }
                if (expectedSession == null) {
                    null
                } else {
                    // Database creation itself takes the native delivery gate. Resolve the complete
                    // working graph before taking that gate for source capture and reconciliation.
                    val database = koin.get<LogDateDatabase>()
                    database.openHelper.writableDatabase
                    // A first media save admits this root through the same non-reentrant gate.
                    // Complete admission before holding that gate for the source merge.
                    admitNativeContent(File(filesDir, "media"))
                    val journals = koin.get<JournalRepository>()
                    Repositories(
                        database = database,
                        expectedSessionId = expectedSession.id,
                        expectedScope = checkNotNull(journals.getJournalRecoveryScope()),
                        journals = journals,
                        notes = koin.get<JournalNotesRepository>(),
                        content = koin.get<JournalContentRepository>(),
                        media = koin.get<MediaManager>(),
                    )
                }
            },
            block = { repositories -> if (repositories == null) false else reconcileLocked(repositories) },
        )
    }

    private suspend fun reconcileLocked(repositories: Repositories): Boolean {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return true
        val workspace = retainedJournalWorkspace(this)
        val session = workspace.current()?.takeIf { it.phase == RetainedJournalWorkspace.Phase.WORKING } ?: return false
        check(session.id == repositories.expectedSessionId) { "Retained workspace changed while opening the working database" }
        check(
            repositories.journals.getJournalRecoveryScope() == repositories.expectedScope &&
                repositories.notes.getNoteRecoveryScope() == repositories.expectedScope,
        ) { "Working account changed while opening reconciliation dependencies" }
        check(repositories.database.isOpen) { "Working database closed before reconciliation" }
        // The native gate excludes active callbacks. A previous process may have left a cohort
        // pending; seal and verify its retained bytes before admitting any independent candidates.
        workspace.finishIncomingDelivery()
        val independentIncoming = retainedIndependentIncomingCandidates(workspace.incomingPayloads().values.flatten())
        val originalSources = workspace.retainedSources()
        val incomingSources = workspace.coherentIncomingPayloads().mapValues { (_, payloads) -> payloads.mapValues { it.value.file } }
        check(originalSources.keys.intersect(incomingSources.keys).isEmpty()) { "Incoming and retained generation IDs overlap" }
        check(session.id in originalSources) { "Active retained generation is missing from its anchored source inventory" }
        val allSources = originalSources + incomingSources
        if (allSources.isEmpty()) return false
        val candidateFiles = (allSources.values + independentIncoming).flatMap(::databaseKeyFiles).distinctBy { it.canonicalPath }
        val mediaSources = allSources.values.toList() + independentIncoming
        val activePreferences = createAndroidMainDataStore(this)
        val scratchRoot = File(noBackupFilesDir, "retained-journal-merge-scratch")
        clearAbandonedDatabaseCopies(scratchRoot)
        var mediaPending = false
        for ((generation, files) in allSources) {
            if (reconcileGeneration(
                    generation,
                    files,
                    candidateFiles,
                    mediaSources,
                    activePreferences,
                    scratchRoot,
                    workspace,
                    session,
                    repositories,
                )
            ) {
                mediaPending = true
            }
        }
        // Deterministic gaps remain in immutable custody. A later completed backup delivery or an
        // explicit user retry schedules another pass; the job must not hot-loop an unchanged source.
        if (mediaPending) Napier.i("Retained journal remains available while source media is pending")
        return false
    }

    private suspend fun reconcileGeneration(
        generation: String,
        files: Map<String, File>,
        candidateFiles: List<File>,
        additionalMediaSources: List<Map<String, File>>,
        activePreferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        scratchRoot: File,
        workspace: RetainedJournalWorkspace,
        session: RetainedJournalWorkspace.Session,
        repositories: Repositories,
    ): Boolean {
        Uuid.parse(generation)
        val databaseFiles =
            files
                .mapNotNull { (path, file) ->
                    path.removePrefix("database/").takeIf { path.startsWith("database/") && it in DATABASE_FILES }?.let { it to file }
                }.toMap()
        if ("logdate" !in databaseFiles) {
            return false
        }
        val preferenceFile = files["files/logdate.preferences_pb"]
        if (preferenceFile == null) {
            return false
        }
        val sourceScope =
            files["source_scope.json"]?.let { anchor ->
                readRetainedJournalSourceScope(preferenceFile, anchor, scratchRoot)
            }
        if (sourceScope == null) {
            return false
        }
        val opened = openRetainedSource(databaseFiles, candidateFiles, scratchRoot)
        if (opened == null) {
            return false
        }
        opened.use {
            val merger =
                RetainedJournalDatabaseMerger(
                    generation,
                    files,
                    opened.database,
                    sourceScope,
                    activePreferences,
                    repositories.journals,
                    repositories.notes,
                    repositories.content,
                    repositories.media,
                    RetainedJournalMergeCheckpointStore(File(noBackupFilesDir, "retained-journal-merges/$generation")),
                    scratchRoot,
                    additionalMediaSources = additionalMediaSources,
                    ensureSourceSessionCurrent = {
                        val current = workspace.current()
                        check(current?.id == session.id && current.phase == RetainedJournalWorkspace.Phase.WORKING) {
                            "Retained journal workspace changed during reconciliation"
                        }
                    },
                )
            val result = merger.merge()
            return result.mediaPending > 0
        }
    }

    private fun openRetainedSource(
        databaseFiles: Map<String, File>,
        candidateFiles: List<File>,
        scratchRoot: File,
    ): app.logdate.client.database.RetainedDatabaseCopy? {
        var opened: app.logdate.client.database.RetainedDatabaseCopy? = null
        for (candidateChunk in candidateFiles.chunked(MAX_KEY_CANDIDATES)) {
            val passphrases = readRetainedDatabasePassphrases(candidateChunk)
            try {
                opened = openRetainedDatabaseCopy(this, databaseFiles, passphrases, scratchRoot)
            } finally {
                passphrases.forEach { it.fill(0) }
            }
            if (opened != null) break
        }
        return opened
    }

    private data class Repositories(
        val database: LogDateDatabase,
        val expectedSessionId: String,
        val expectedScope: JournalMergeScope,
        val journals: JournalRepository,
        val notes: JournalNotesRepository,
        val content: JournalContentRepository,
        val media: MediaManager,
    )

    private fun databaseKeyFiles(files: Map<String, File>): List<File> =
        files
            .filter { (path, _) ->
                path.substringAfterLast('/') in setOf("passphrase_backup", "working_passphrase_backup") &&
                    !path.endsWith(".part") &&
                    (path.startsWith("db_recovery/") || path.startsWith("files/db_recovery/"))
            }.values
            .toList()

    private fun clearAbandonedDatabaseCopies(root: File) {
        val expectedParent = noBackupFilesDir.canonicalFile
        check(root.absoluteFile.parentFile?.canonicalFile == expectedParent && root.name == "retained-journal-merge-scratch")
        if (root.exists()) {
            check(Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root.toPath()))
            root
                .listFiles()
                .orEmpty()
                .filter { candidate ->
                    (candidate.name.startsWith("retained-db-") || candidate.name.startsWith("preferences-read-")) &&
                        Files.isDirectory(candidate.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(candidate.toPath())
                }.forEach { candidate -> check(candidate.deleteRecursively()) }
        } else {
            check(root.mkdirs())
        }
    }

    private companion object {
        const val RECONCILIATION_TIMEOUT_MS = 5 * 60 * 1000L
        const val MAX_KEY_CANDIDATES = 128
        val DATABASE_FILES = setOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal")
    }
}
