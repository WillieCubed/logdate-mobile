package app.logdate.feature.timeline.domain

import android.util.Log
import app.logdate.core.data.notes.JournalNote
import app.logdate.core.network.NetworkAvailabilityMonitor
import javax.inject.Inject

/**
 * A use case that summarizes journal entries.
 *
 * This relies on a generative model to summarize text entries.
 *
 * TODO: Ensure summarization works offline
 */
class SummarizeJournalEntriesUseCase @Inject constructor(
    private val summarizer: EntrySummarizer,
    private val networkAvailabilityMonitor: NetworkAvailabilityMonitor,
) {
    suspend operator fun invoke(entries: List<JournalNote>): SummarizeJournalEntriesResult {
        if (entries.isEmpty()) {
            return SummarizeJournalEntriesResult.SummaryUnavailable
        }
        if (!networkAvailabilityMonitor.isNetworkAvailable()) {
            return SummarizeJournalEntriesResult.NetworkUnavailable
        }

        val summaryPrompt = entries.filter { it is JournalNote.Text }
            .joinToString("\n") { (it as JournalNote.Text).content }
        val summaryKey = entries.joinToString("_") { it.uid }
        try {
            val summary = summarizer.summarize(summaryKey, summaryPrompt)
            if (summary == null) {
                Log.w(
                    "SummarizeJournalEntriesUseCase",
                    "External model could not summarize entries"
                )
                return SummarizeJournalEntriesResult.SummaryUnavailable
            }
            return SummarizeJournalEntriesResult.Success(summary)
        } catch (e: Exception) {
            Log.e("SummarizeJournalEntriesUseCase", "Could not summarize journal entries", e)
            return SummarizeJournalEntriesResult.SummaryUnavailable
        }
    }
}

sealed interface SummarizeJournalEntriesResult {
    /**
     * A summary could not be generated because the client was not connected to the internet.
     *
     * TODO: Support on-device models
     */
    data object NetworkUnavailable : SummarizeJournalEntriesResult

    /**
     * A summary was unavailable because the request was invalid.
     *
     * This may be returned if there was not enough or no entries available to be summarized.
     */
    data object SummaryUnavailable : SummarizeJournalEntriesResult

    /**
     * Journal entries were able to be summarized.
     */
    data class Success(val summary: String) : SummarizeJournalEntriesResult
}