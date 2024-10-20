package app.logdate.core.data.journals

import app.logdate.core.coroutines.AppDispatcher.IO
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.data.journals.util.toEntity
import app.logdate.core.data.journals.util.toModel
import app.logdate.core.database.dao.JournalDao
import app.logdate.core.di.ApplicationScope
import app.logdate.model.Journal
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.suspendCoroutine

/**
 * A [JournalRepository] that uses a local database as the single source of truth.
 *
 * Note that this repository is an interface for all journals stored on device, not necessarily
 * journals that belong to any given user. To access user-specific data for journals, use
 * a [JournalUserDataRepository].
 */
class OfflineFirstJournalRepository @Inject constructor(
    private val journalDao: JournalDao,
    private val remoteDataSource: RemoteDataSource,
    @ApplicationScope private val externalScope: CoroutineScope,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : JournalRepository {

    override val allJournalsObserved: Flow<List<Journal>>
        get() = journalDao.observeAll().map { journals ->
            journals.map { it.toModel() }
        }

    override fun observeJournalById(id: String): Flow<Journal> {
        return journalDao.observeJournalById(id).map { it.toModel() }
    }

    override suspend fun create(journal: Journal): String = withContext(dispatcher) {
        externalScope.async {
            journalDao.create(journal.toEntity()).toString()
        }.await()
    }

    override suspend fun delete(journalId: String) = withContext(dispatcher) {
        externalScope.async {
            journalDao.delete(journalId)
        }.await()
    }

    private fun syncLocalWithRemote() {
        externalScope.launch(dispatcher) {
            val localJournals = journalDao.getAll()
            val remoteJournals = remoteDataSource.observeAllJournals()
        }
    }
}

class RemoteDataSource @Inject constructor() {

    private val db = Firebase.firestore

    private companion object {
        private const val JOURNALS_COLLECTION = "journals"
    }


    suspend fun observeAllJournals() = suspendCoroutine { continuation ->
        db.collection(JOURNALS_COLLECTION)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    continuation.resumeWith(Result.failure(error))
                } else {
                    val journals =
                        value?.documents?.map { it.toObject(Journal::class.java)!! } ?: emptyList()
                    continuation.resumeWith(Result.success(journals))
                }
            }
    }

    suspend fun addJournal(journal: Journal) = suspendCoroutine {
        db.collection("journals").add(
            mapOf(
                "title" to journal.id,
                "content" to journal.lastUpdated,
                "lastUpdated" to journal.created,
                "description" to journal.description,
            )
        ).addOnSuccessListener { documentReference ->
            it.resumeWith(Result.success(documentReference.id))
        }.addOnFailureListener { exception ->
            it.resumeWith(Result.failure(exception))
        }
    }

    suspend fun editJournal(journal: Journal) = suspendCoroutine { continuation ->
        db.collection(JOURNALS_COLLECTION)
            .document(journal.id)
            .set(
                mapOf(
                    "title" to journal.id,
                    "content" to journal.lastUpdated,
                    "lastUpdated" to journal.created,
                    "description" to journal.description,
                )
            )
            .addOnSuccessListener {
                continuation.resumeWith(Result.success(Unit))
            }.addOnFailureListener { exception ->
                continuation.resumeWith(Result.failure(exception))
            }
    }

    suspend fun deleteJournal(journalId: String) = suspendCoroutine { continuation ->
        db.collection(JOURNALS_COLLECTION)
            .document(journalId)
            .delete()
            .addOnSuccessListener {
                continuation.resumeWith(Result.success(Unit))
            }.addOnFailureListener { exception ->
                continuation.resumeWith(Result.failure(exception))
            }
    }
}