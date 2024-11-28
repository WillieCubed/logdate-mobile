package app.logdate.client.domain.timeline

import app.logdate.client.intelligence.EntrySummarizer
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier

/**
 * A use case that summarizes journal entries.
 *
 * This relies on a generative model to summarize text entries.
 *
 * TODO: Ensure summarization works offline
 */
class SummarizeJournalEntriesUseCase(
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

        val summaryPrompt = entries.filterIsInstance<JournalNote.Text>()
            .joinToString("\n") { it.content }
        val summaryKey = entries.joinToString("_") { it.uid }
        try {
            val summary = summarizer.summarize(summaryKey, summaryPrompt)
            if (summary == null) {
                Napier.w(
                    tag =
                    "SummarizeJournalEntriesUseCase",
                    message =
                    "External model could not summarize entries"
                )
                return SummarizeJournalEntriesResult.SummaryUnavailable
            }
            return SummarizeJournalEntriesResult.Success(summary)
        } catch (e: Exception) {
            Napier.e(
                tag = "SummarizeJournalEntriesUseCase",
                throwable = e,
                message = "Could not summarize journal entries",
            )
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