package app.logdate.client.data.restore

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.UserManager
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.openRetainedDatabaseCopy
import app.logdate.client.database.readRetainedDatabasePassphrases
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
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
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.uuid.Uuid

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
        if (!getSystemService(UserManager::class.java).isUserUnlocked) {
            return true
        }
        val koin =
            GlobalContext.getOrNull() ?: run {
                return true
            }
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
                    val expectedScope = checkNotNull(journals.getJournalRecoveryScope())
                    Repositories(
                        database = database,
                        expectedSessionId = expectedSession?.id,
                        expectedScope = expectedScope,
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
                    RetainedJournalReconciliationLock.withMediaClaim(
                        app.logdate.client.media.storage
                            .AndroidMediaClaimFence(noBackupFilesDir),
                    ) {
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
        if (allSources.isEmpty()) {
            return repairIndependentMedia(independentIncoming, repositories, sourcesCovered = true)
        }
        val candidateFiles = (allSources.values + independentIncoming).flatMap(::databaseKeyFiles).distinctBy { it.canonicalPath }
        val mediaSources = allSources.values.toList() + independentIncoming
        val activePreferences = createAndroidMainDataStore(this)
        val scratchRoot = File(noBackupFilesDir, "retained-journal-merge-scratch")
        clearAbandonedDatabaseCopies(scratchRoot, noBackupFilesDir)
        clearAbandonedDraftCopies(File(noBackupFilesDir, "retained-draft-reads"), noBackupFilesDir)
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
        if (!shouldReconcileRetainedMedia(candidates, NativeJournalReplayCustody(this).sealedPayloads())) {
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
        if ("logdate" !in databaseFiles) return NativeSourceOutcome(false, false)
        val preferenceFile = files["files/logdate.preferences_pb"] ?: return NativeSourceOutcome(false, false)
        val sourceScope =
            files["source_scope.json"]?.let { anchor ->
                readRetainedJournalSourceScope(preferenceFile, anchor, scratchRoot, databaseFiles.mapKeys { "database/${it.key}" })
            } ?: return NativeSourceOutcome(false, false)
        val opened = openRetainedSource(databaseFiles, candidateFiles, scratchRoot) ?: return NativeSourceOutcome(false, false)
        opened.copy.use {
            verifyEncryptedScope(files, opened.copy.database, sourceScope, preferenceFile)
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
            val receiptFile = files["source_scope.json"]
            if (receiptFile == null) {
                if (result.coverageComplete) admitMergedSource(generation, opened.keyFile)
                return NativeSourceOutcome(result.mediaPending > 0, result.coverageComplete)
            }
            // Camera originals are validated against this receipt and the current owner scope;
            // import their pending draft even when unrelated database tables remain uncovered.
            // Keep the source in custody until full database coverage is proven.
            val imported =
                importPendingCameraDrafts(generation, files, receiptFile, opened, sourceScope, result.coverageComplete, repositories)
            if (!imported) return NativeSourceOutcome(true, false)
            return NativeSourceOutcome(result.mediaPending > 0, result.coverageComplete)
        }
    }

    private suspend fun importPendingCameraDrafts(
        generation: String,
        files: Map<String, File>,
        receiptFile: File,
        opened: RetainedOpenedSource,
        sourceScope: RetainedJournalSourceScope,
        coverageComplete: Boolean,
        repositories: Repositories,
    ): Boolean {
        val receipt =
            runCatching {
                Json.decodeFromString<app.logdate.client.device.restore.NativeJournalSourceReceipt>(receiptFile.readText())
            }.getOrElse {
                return false
            }
        val importer = pendingCameraDraftImporter(sourceScope, repositories)
        return importReceiptPinnedCameraDrafts(
            coverageComplete = coverageComplete,
            import = {
                importer.import(generation, files, receipt.mediaInventorySha256)
            },
            admitSource = { admitMergedSource(generation, opened.keyFile) },
        )
    }

    private suspend fun pendingCameraDraftImporter(
        sourceScope: RetainedJournalSourceScope,
        repositories: Repositories,
    ): NativePendingCameraDraftImporter {
        val job = currentCoroutineContext()[Job]
        val dataStore = createAndroidMainDataStore(this)
        val isCurrent = {
            job?.isActive != false &&
                repositories.database.isOpen &&
                normalizeBackendOrigin(repositories.backendOrigin()) ==
                normalizeBackendOrigin(repositories.expectedScope.serverOrigin)
        }
        return NativePendingCameraDraftImporter(
            filesDir,
            packageName,
            repositories.media,
            repositories.entryDrafts,
            isCurrent,
            {
                check(isCurrent()) { "Native pending camera delivery changed" }
                requireSourceScopeCurrent(dataStore.data.first(), sourceScope, repositories)
            },
            scopeFence = { commit ->
                var applied = false
                dataStore.updateData { current ->
                    check(isCurrent()) { "Native pending camera delivery changed" }
                    requireSourceScopeCurrent(current, sourceScope, repositories)
                    applied = commit()
                    current
                }
                applied
            },
        )
    }

    private fun requireSourceScopeCurrent(
        current: androidx.datastore.preferences.core.Preferences,
        sourceScope: RetainedJournalSourceScope,
        repositories: Repositories,
    ) {
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

    private fun mergerFor(
        generation: String,
        files: Map<String, File>,
        opened: RetainedOpenedSource,
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
            RetainedJournalMergeCheckpointStore(retainedJournalMergeCheckpointDirectory(noBackupFilesDir, generation)),
            scratchRoot,
            additionalMediaSources = additionalMediaSources,
            ensureSourceSessionCurrent = {
                val current = workspace.current()
                check(current?.id == session?.id && (current == null || current.phase == RetainedJournalWorkspace.Phase.WORKING)) {
                    "Retained journal workspace changed during reconciliation"
                }
            },
        )

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

    private fun openRetainedSource(
        databaseFiles: Map<String, File>,
        candidateFiles: List<File>,
        scratchRoot: File,
    ): RetainedOpenedSource? {
        for (candidate in candidateFiles) {
            val passphrases = readRetainedDatabasePassphrases(listOf(candidate))
            try {
                openRetainedDatabaseCopy(this, databaseFiles, passphrases, scratchRoot)?.let { return RetainedOpenedSource(it, candidate) }
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

    private companion object {
        const val RECONCILIATION_TIMEOUT_MS = 5 * 60 * 1000L
        val DATABASE_FILES = setOf("logdate", "logdate-wal", "logdate-shm", "logdate-journal")
    }
}
