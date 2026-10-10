package app.logdate.client.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import app.logdate.client.ambient.quickReplyNote
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.repository.search.SearchRepository
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.TimeZone
import kotlin.time.Clock

/**
 * LogDate's assistant actions on Android 16 and later. An assistant can save an entry at any time,
 * because that reveals nothing. Reading, which means listing journals or searching entries, needs the
 * person's consent (system search visibility) and an unlocked app; otherwise the assistant is told
 * why and what the person can do.
 *
 * The AppFunctions compiler generates `LogDateAppFunctionService` from this class, with the schema
 * `logdate_app_functions.xml`. The service is declared disabled and enabled by
 * `PlatformComponentToggler` while the APP_FUNCTIONS flag is on.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "LogDateAppFunctionService",
    appFunctionXmlFileName = "logdate_app_functions",
)
abstract class LogDateAppFunctions :
    AppFunctionService(),
    KoinComponent {
    private val addNote: AddNoteUseCase by inject()
    private val journals: GetCurrentUserJournalsUseCase by inject()
    private val search: SearchRepository by inject()
    private val preferences: LogdatePreferencesDataSource by inject()
    private val gatekeeper: BiometricGatekeeper by inject()

    /**
     * Saves a new text entry in the person's LogDate journal, dated now. Use it when the person asks
     * to write down, note, or journal something.
     *
     * @param text The words of the entry, exactly as the person said or wrote them.
     * @return The saved entry.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createTextEntry(text: String): AssistantEntry =
        withContext(Dispatchers.IO) {
            val note =
                quickReplyNote(text, Clock.System.now(), TimeZone.getDefault().id)
                    ?: throw AppFunctionInvalidArgumentException("The entry text is empty.")
            addNote(notes = listOf(note))
            AssistantEntry(id = note.uid.toString(), text = assistantExcerpt(note.content), createdAt = note.creationTimestamp.toString())
        }

    /**
     * Lists the person's LogDate journals by title, so they can be named when the person talks
     * about one. Only works when the person has turned on system search visibility in LogDate and
     * LogDate is unlocked; otherwise it fails with a message to pass on.
     *
     * @return Every journal the person has.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listJournals(): List<AssistantJournal> =
        withContext(Dispatchers.IO) {
            requireReadAccess()
            journals().first().map { AssistantJournal(id = it.id.toString(), title = it.title) }
        }

    /**
     * Searches the person's LogDate entries for words or phrases. Only works when the person has
     * turned on system search visibility in LogDate and LogDate is unlocked; otherwise it fails with
     * a message to pass on.
     *
     * @param query The words to look for.
     * @return Up to 10 matching entries, best match first.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchEntries(query: String): List<AssistantEntry> =
        withContext(Dispatchers.IO) {
            requireReadAccess()
            if (query.isBlank()) throw AppFunctionInvalidArgumentException("The search query is empty.")
            search.searchWithLimit(query, ASSISTANT_SEARCH_LIMIT).first().map { result ->
                AssistantEntry(id = result.uid.toString(), text = assistantExcerpt(result.content), createdAt = result.created.toString())
            }
        }

    /** Refuses the call, with the reason to pass on, unless the person allows reading right now. */
    private suspend fun requireReadAccess() {
        // The gatekeeper starts out as "no prompt needed" in every new process, so whether the app
        // lock is on comes from the setting itself.
        val appLockOn = preferences.userData.first().securityLevel != AppSecurityLevel.NONE
        val unlocked = assistantUnlocked(appLockOn, authenticated = gatekeeper.authState.value == AppAuthState.AUTHENTICATED)
        assistantReadRefusal(preferences.getSystemSearchVisibilityEnabled(), unlocked)?.let { refusal ->
            throw AppFunctionDeniedException(refusal.message)
        }
    }
}

/** An entry as an assistant sees it. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantEntry(
    /** The entry's stable id. */
    val id: String,
    /** The entry's text on one line, shortened when long. */
    val text: String,
    /** When the entry was written, as an ISO 8601 instant. */
    val createdAt: String,
)

/** A journal as an assistant sees it. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AssistantJournal(
    /** The journal's stable id. */
    val id: String,
    /** The journal's title. */
    val title: String,
)
