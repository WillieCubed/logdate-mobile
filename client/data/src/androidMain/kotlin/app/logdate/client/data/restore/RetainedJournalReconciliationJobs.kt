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
import app.logdate.client.device.restore.NativeJournalReplayCustody
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    if (runCatching { workspace.current()?.phase == RetainedJournalWorkspace.Phase.WORKING }.getOrDefault(false) ||
        NativeJournalReplayCustody(context).hasInputs()
    ) {
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
                if (expectedSession == null && !NativeJournalReplayCustody(this).hasInputs()) {
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
                    val config = koin.get<app.logdate.shared.config.LogDateConfigRepository>()
                    Repositories(
                        database = database,
                        expectedSessionId = expectedSession?.id,
                        expectedScope = checkNotNull(journals.getJournalRecoveryScope()),
                        journals = journals,
                        notes = koin.get<JournalNotesRepository>(),
                        content = koin.get<JournalContentRepository>(),
                        media = koin.get<MediaManager>(),
                        entryDrafts = koin.get<app.logdate.client.repository.journals.EntryDraftRecoveryRepository>(),
                        mediaReferences = koin.get<app.logdate.client.media.storage.StoredMediaReferences>(),
                        backendOrigin = { config.backendUrl.value },
                    )
                }
            },
            block = { repositories ->
                if (repositories == null) {
                    false
                } else {
                    app.logdate.client.media.storage.AndroidMediaClaimFence(noBackupFilesDir).withClaim {
                        reconcileLocked(repositories)
                    }
                }
            },
        )
    }

    private suspend fun reconcileLocked(repositories: Repositories): Boolean {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return true
        val workspace = retainedJournalWorkspace(this)
        val session = workspace.current()
        check(session == null || session.phase == RetainedJournalWorkspace.Phase.WORKING)
        check(session?.id == repositories.expectedSessionId) { "Retained workspace changed while opening the working database" }
        check(
            repositories.journals.getJournalRecoveryScope() == repositories.expectedScope &&
                repositories.notes.getNoteRecoveryScope() == repositories.expectedScope,
        ) { "Working account changed while opening reconciliation dependencies" }
        check(repositories.database.isOpen) { "Working database closed before reconciliation" }
        // The native gate excludes active callbacks. A previous process may have left a cohort
        // pending; seal and verify its retained bytes before admitting any independent candidates.
        if (session != null) workspace.finishIncomingDelivery()
        val incomingPayloads =
            (if (session != null) workspace.incomingPayloads().values.flatten() else emptyList()) +
                NativeJournalReplayCustody(this).sealedPayloads()
        val independentIncoming = retainedIndependentIncomingCandidates(incomingPayloads)
        val originalSources = if (session != null) workspace.retainedSources() else emptyMap()
        val incomingSources =
            app.logdate.client.device.restore
                .verifiedNativeJournalSources(incomingPayloads)
        check(originalSources.keys.intersect(incomingSources.keys).isEmpty()) { "Incoming and retained generation IDs overlap" }
        if (session !=
            null
        ) {
            check(session.id in originalSources) { "Active retained generation is missing from its anchored source inventory" }
        }
        val allSources = originalSources + incomingSources
        if (allSources.isEmpty()) return repairIndependentMedia(independentIncoming, repositories, sourcesCovered = true)
        val candidateFiles = (allSources.values + independentIncoming).flatMap(::databaseKeyFiles).distinctBy { it.canonicalPath }
        val mediaSources = allSources.values.toList() + independentIncoming
        val activePreferences = createAndroidMainDataStore(this)
        val scratchRoot = File(noBackupFilesDir, "retained-journal-merge-scratch")
        clearAbandonedDatabaseCopies(scratchRoot)
        var mediaPending = false
        var allSourcesCovered = true
        for ((generation, files) in allSources) {
            val outcome =
                reconcileGeneration(
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
            if (outcome.mediaPending) mediaPending = true
            allSourcesCovered = allSourcesCovered && outcome.coverageComplete
        }
        // Deterministic gaps remain in immutable custody. A later completed backup delivery or an
        // explicit user retry schedules another pass; the job must not hot-loop an unchanged source.
        if (mediaPending) Napier.i("Retained journal remains available while source media is pending")
        refreshStagedNativeMediaInventories(this)
        retireStagedNativeAuxiliary(this, repositories.expectedScope, createAndroidMainDataStore(this), allSourcesCovered)
        return repairIndependentMedia(independentIncoming, repositories, allSourcesCovered)
    }

    private suspend fun repairIndependentMedia(
        candidates: List<Map<String, File>>,
        repositories: Repositories,
        sourcesCovered: Boolean,
    ): Boolean {
        refreshStagedNativeMediaInventories(this)
        retireStagedNativeAuxiliary(this, repositories.expectedScope, createAndroidMainDataStore(this), sourcesCovered)
        if (candidates.none { files ->
                files.keys.any { path ->
                    listOf("files/media/", "files/user_media/", "files/audio_notes/").any(path::startsWith)
                }
            }
        ) {
            return false
        }
        val job = kotlinx.coroutines.currentCoroutineContext()[Job]
        val authority =
            app.logdate.client.device.restore
                .NativeJournalRestoreAuthority(this)
        val proofs =
            NativeJournalStagedSourceIndex(this)
                .admittedSources()
                .mapValues { (_, record) ->
                    record.receipt to record.mediaInventory
                }.toMutableMap()
        authority.completedReceipt()?.let { receipt ->
            authority.completedSourceReceipt()?.let { source -> proofs[receipt] = source to authority.completedMediaInventory() }
        }
        var retry = false
        for ((receipt, proof) in proofs) {
            val result =
                repairNativeLateMedia(
                    candidates,
                    NativeLateMediaRepositories(
                        repositories.journals,
                        repositories.notes,
                        repositories.entryDrafts,
                        createAndroidMainDataStore(this),
                        repositories.media,
                        repositories.mediaReferences,
                    ),
                    NativeJournalReplayCustody(this),
                    repositories.expectedScope,
                    completedSource = proof.first,
                    completedInventory = proof.second,
                    sourceReceiptSha256 = receipt,
                    packageName = packageName,
                    legacyReceiptsRoot = File(noBackupFilesDir, "native-legacy-media-imports"),
                    isDeliveryCurrent = {
                        job?.isActive != false &&
                            repositories.database.isOpen &&
                            normalizeBackendOrigin(repositories.backendOrigin()) ==
                            normalizeBackendOrigin(repositories.expectedScope.serverOrigin)
                    },
                )
            retry = retry || result.retry
        }
        return retry
    }

    private suspend fun reconcileGeneration(
        generation: String,
        files: Map<String, File>,
        candidateFiles: List<File>,
        additionalMediaSources: List<Map<String, File>>,
        activePreferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        scratchRoot: File,
        workspace: RetainedJournalWorkspace,
        session: RetainedJournalWorkspace.Session?,
        repositories: Repositories,
    ): NativeSourceOutcome {
        Uuid.parse(generation)
        val databaseFiles =
            files
                .mapNotNull { (path, file) ->
                    path.removePrefix("database/").takeIf { path.startsWith("database/") && it in DATABASE_FILES }?.let { it to file }
                }.toMap()
        if ("logdate" !in databaseFiles) {
            return NativeSourceOutcome(false, false)
        }
        val preferenceFile = files["files/logdate.preferences_pb"]
        if (preferenceFile == null) {
            return NativeSourceOutcome(false, false)
        }
        val sourceScope =
            files["source_scope.json"]?.let { anchor ->
                readRetainedJournalSourceScope(preferenceFile, anchor, scratchRoot, databaseFiles.mapKeys { "database/${it.key}" })
            }
        if (sourceScope == null) {
            return NativeSourceOutcome(false, false)
        }
        val opened = openRetainedSource(databaseFiles, candidateFiles, scratchRoot)
        if (opened == null) {
            return NativeSourceOutcome(false, false)
        }
        opened.copy.use {
            verifyEncryptedScope(files, opened, sourceScope, preferenceFile)
            val merger =
                mergerFor(
                    generation,
                    files,
                    opened,
                    sourceScope,
                    activePreferences,
                    scratchRoot,
                    additionalMediaSources,
                    workspace,
                    session,
                    repositories,
                )
            val result = merger.merge()
            if (result.coverageComplete) {
                val receiptFile = files["source_scope.json"]
                if (receiptFile != null) {
                    val receipt =
                        runCatching {
                            Json.decodeFromString<app.logdate.client.device.restore.NativeJournalSourceReceipt>(receiptFile.readText())
                        }.getOrNull() ?: return NativeSourceOutcome(true, false)
                    val job = currentCoroutineContext()[Job]
                    val dataStore = createAndroidMainDataStore(this)
                    val isCurrent = {
                        job?.isActive != false &&
                            repositories.database.isOpen &&
                            normalizeBackendOrigin(repositories.backendOrigin()) ==
                            normalizeBackendOrigin(repositories.expectedScope.serverOrigin)
                    }

                    suspend fun requireSourceCurrent() {
                        check(isCurrent()) { "Native pending camera delivery changed" }
                        val current = dataStore.data.first()
                        val actual = RetainedJournalSourceScope.from(current, sourceScope.backendOrigin)
                        check(
                            actual != null &&
                                sourceScope.matchesCurrentOrAdopted(actual, current) &&
                                sourceScope.matchesRepositoryScope(
                                    repositories.expectedScope.ownerId,
                                    repositories.expectedScope.serverOrigin,
                                ),
                        ) { "Native pending camera owner or backend changed" }
                    }
                    val importer =
                        NativePendingCameraDraftImporter(
                            filesDir,
                            packageName,
                            repositories.media,
                            repositories.entryDrafts,
                            isCurrent,
                            ::requireSourceCurrent,
                        ) { commit ->
                            var applied = false
                            dataStore.updateData { current ->
                                check(isCurrent()) { "Native pending camera delivery changed" }
                                val actual = RetainedJournalSourceScope.from(current, sourceScope.backendOrigin)
                                check(
                                    actual != null &&
                                        sourceScope.matchesCurrentOrAdopted(actual, current) &&
                                        sourceScope.matchesRepositoryScope(
                                            repositories.expectedScope.ownerId,
                                            repositories.expectedScope.serverOrigin,
                                        ),
                                ) { "Native pending camera owner or backend changed" }
                                applied = commit()
                                current
                            }
                            applied
                        }
                    if (!importer.import(generation, files, receipt.mediaInventorySha256)) {
                        return NativeSourceOutcome(true, false)
                    }
                }
                admitMergedSource(generation, opened.keyFile)
            }
            return NativeSourceOutcome(result.mediaPending > 0, result.coverageComplete)
        }
    }

    private fun mergerFor(
        generation: String,
        files: Map<String, File>,
        opened: OpenedSource,
        sourceScope: RetainedJournalSourceScope,
        activePreferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        scratchRoot: File,
        additionalMediaSources: List<Map<String, File>>,
        workspace: RetainedJournalWorkspace,
        session: RetainedJournalWorkspace.Session?,
        repositories: Repositories,
    ): RetainedJournalDatabaseMerger =
        RetainedJournalDatabaseMerger(
            generation,
            files,
            opened.copy.database,
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
                check(current?.id == session?.id && (current == null || current.phase == RetainedJournalWorkspace.Phase.WORKING)) {
                    "Retained journal workspace changed during reconciliation"
                }
            },
        )

    private fun verifyEncryptedScope(
        files: Map<String, File>,
        opened: OpenedSource,
        sourceScope: RetainedJournalSourceScope,
        preferenceFile: File,
    ) {
        if (files["source_scope.json"]?.readText()?.let {
                Json
                    .parseToJsonElement(it)
                    .jsonObject["version"]
                    ?.jsonPrimitive
                    ?.intOrNull
            } in setOf(2, 3)
        ) {
            check(
                app.logdate.client.database
                    .readNativeJournalSnapshotScope(opened.copy.database) ==
                    app.logdate.client.database.NativeJournalSnapshotScope(
                        checkNotNull(sourceScope.ownerId),
                        sourceScope.bound,
                        sourceScope.backendOrigin,
                        nativeSnapshotChecksum(preferenceFile),
                    ),
            ) { "Encrypted source scope does not match native preferences" }
        }
    }

    private fun admitMergedSource(
        generation: String,
        keyFile: File,
    ) {
        val custody = NativeJournalReplayCustody(this)
        app.logdate.client.device.restore.verifiedNativeJournalSources(custody.sealedPayloads())[generation]?.let { accepted ->
            val receipt = nativeSnapshotChecksum(accepted.getValue("source_scope.json"))
            val stagedIndex = NativeJournalStagedSourceIndex(this)
            val sourceFiles =
                accepted
                    .mapKeys { (path, _) ->
                        if (path == "source_scope.json") {
                            app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
                        } else {
                            path
                        }
                    }.toMutableMap()
            custody
                .sealedPayloads()
                .firstOrNull { payload ->
                    payload.file.canonicalFile == keyFile.canonicalFile
                }?.let { key ->
                    sourceFiles[key.path.removePrefix(app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX)] =
                        key.file
                }
            stagedIndex.remember(sourceFiles)
            stagedIndex.admitAfterMerged(receipt)
            custody.acknowledgeMergedSource(
                receipt,
                accepted,
                stagedIndex.admittedSources().getValue(receipt).sourceCorePhysicalPaths,
            )
        }
    }

    private data class NativeSourceOutcome(
        val mediaPending: Boolean,
        val coverageComplete: Boolean,
    )

    private data class OpenedSource(
        val copy: app.logdate.client.database.RetainedDatabaseCopy,
        val keyFile: File,
    )

    private fun openRetainedSource(
        databaseFiles: Map<String, File>,
        candidateFiles: List<File>,
        scratchRoot: File,
    ): OpenedSource? {
        for (candidate in candidateFiles) {
            val passphrases = readRetainedDatabasePassphrases(listOf(candidate))
            try {
                openRetainedDatabaseCopy(this, databaseFiles, passphrases, scratchRoot)?.let { return OpenedSource(it, candidate) }
            } finally {
                passphrases.forEach { it.fill(0) }
            }
        }
        return null
    }

    private data class Repositories(
        val database: LogDateDatabase,
        val expectedSessionId: String?,
        val expectedScope: JournalMergeScope,
        val journals: JournalRepository,
        val notes: JournalNotesRepository,
        val content: JournalContentRepository,
        val media: MediaManager,
        val entryDrafts: app.logdate.client.repository.journals.EntryDraftRecoveryRepository,
        val mediaReferences: app.logdate.client.media.storage.StoredMediaReferences,
        val backendOrigin: () -> String,
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
