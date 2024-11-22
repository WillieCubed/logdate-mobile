package app.logdate.feature.timeline.domain

import android.util.Log
import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.data.timeline.cache.GenerativeAICache
import app.logdate.core.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.core.intelligence.generativeai.GenerativeAIChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A utility that summarizes journal entries.
 *
 * This currently only supports text entries.
 *
 * TODO: Incorporate other information into summarizes, such as people and places.
 */
@Singleton
class EntrySummarizer @Inject constructor(
    private val generativeAICache: GenerativeAICache,
    private val genAIClient: GenerativeAIChatClient,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful journal summarizer. Only speak in the second person to the user, and in the past tense. Do not respond with anything other than a one or two-sentence summary of the following journal entries:"
    }

    /**
     * Summarizes a journal entry.
     *
     * @param summaryId An ID used to cache the response.
     * @param text The text of the journal entry to summarize.
     * @param useCached Whether to use a cached response if available. If false, the response will
     * be generated from scratch.
     */
    suspend fun summarize(summaryId: String, text: String, useCached: Boolean = true): String? =
        withContext(ioDispatcher) {
            if (useCached) {
                val cachedResponse = generativeAICache.getEntry(summaryId)
                if (cachedResponse != null) {
                    Log.d("EntrySummarizer", "Using cached response for $summaryId")
                    return@withContext cachedResponse.content
                }
            }
            Log.d("EntrySummarizer", "No cached response for $summaryId, generating new response")
            val response = genAIClient.submit(
                listOf(
                    GenerativeAIChatMessage("system", SYSTEM_PROMPT),
                    GenerativeAIChatMessage("user", text),
                )
            )
            // Cache responses
            if (response != null) {
                Log.d("EntrySummarizer", "Caching response for:\n$text")
                generativeAICache.putEntry(summaryId, response)
            }
            response ?: "No summary available"
        }
}

