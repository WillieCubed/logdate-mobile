package app.logdate.wear.playback

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.CancellationException

/** Chooses verified phone media without replacing a managed note's logical reference. */
class WindowPhoneAudioResolver(
    private val legacy: WearSyncedAudioResolver,
    private val enabled: suspend () -> Boolean,
    private val managed: suspend (JournalNote.Audio) -> Boolean,
    private val ownedRecording: (JournalNote.Audio) -> Boolean,
    private val prepare: suspend (JournalNote.Audio) -> String,
) : WearSyncedAudioResolver {
    override suspend fun resolvePlayableUri(note: JournalNote.Audio): Result<String> =
        try {
            if (!enabled() || (!managed(note) && ownedRecording(note))) {
                legacy.resolvePlayableUri(note)
            } else {
                Result.success(prepare(note))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
