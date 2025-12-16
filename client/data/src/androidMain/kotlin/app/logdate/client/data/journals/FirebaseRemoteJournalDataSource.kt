package app.logdate.client.data.journals

import app.logdate.shared.model.Journal
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.coroutines.suspendCoroutine

/**
 * A journal data source that uses Cloud Firestore as its backing store.
 */
class FirebaseRemoteJournalDataSource : RemoteJournalDataSource {

    private val db = Firebase.firestore

    private companion object {
        private const val JOURNALS_COLLECTION = "journals"
    }

    override suspend fun observeAllJournals() = suspendCoroutine { continuation ->
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

    override suspend fun addJournal(journal: Journal) = suspendCoroutine {
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

    override suspend fun editJournal(journal: Journal) = suspendCoroutine { continuation ->
        db.collection(JOURNALS_COLLECTION)
            .document(journal.id.toString())
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

    override suspend fun deleteJournal(journalId: String) = suspendCoroutine { continuation ->
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