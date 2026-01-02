package app.logdate.client.intelligence.entity.people

import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.AIUnavailableReason
import app.logdate.client.intelligence.cache.GenerativeAICache
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.shared.model.Person
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * A utility to extract people's names from text.
 */
@OptIn(ExperimentalUuidApi::class)
class PeopleExtractor(
    private val generativeAICache: GenerativeAICache,
    private val generativeAIChatClient: GenerativeAIChatClient,
    private val networkAvailabilityMonitor: NetworkAvailabilityMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        // TODO: Use additional logic to extract text fragments that could be resolved to people
        private const val ADVANCED_EXTRACTION_PROMPT = """
You are a system utility that extracts the names of likely humans mentioned in text.
Only literally return the names from the text. Each name must be separated by a new
line. Include references to noun-adjective parings that could be names.
        """
        private const val EXTRACTION_PROMPT = """
You are a system utility that extracts the names of likely humans mentioned in text.
Only literally return the names from the text. Each name must be separated by a new
line.
        """
    }

    /**
     * Extracts people's names from the given text.
     *
     * @param documentId An ID used to identify and cache the response.
     * @param text The text to extract people's names from.
     * @param useCached Whether to use a cached response if available. If false, the response will
     * be generated from scratch.
     *
     * @return A list of people's names extracted from the text.
     */
    suspend fun extractPeople(
        documentId: String,
        text: String,
        useCached: Boolean = true,
    ): AIResult<List<Person>> =
        withContext(ioDispatcher) {
            if (useCached) {
                val cachedResponse = generativeAICache.getEntry(documentId)
                if (cachedResponse != null) {
                    return@withContext AIResult.Success(
                        cachedResponse.content.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { Person(name = it) },
                        fromCache = true
                    )
                }
            }
            if (!networkAvailabilityMonitor.isNetworkAvailable()) {
                return@withContext AIResult.Unavailable(AIUnavailableReason.NoNetwork)
            }
            val prompts = listOf(
                GenerativeAIChatMessage("system", EXTRACTION_PROMPT),
                GenerativeAIChatMessage("user", text),
                // TODO: Use structured response format
            )
            try {
                val response = generativeAIChatClient.submit(prompts)
                if (response != null) {
                    Napier.d(tag = "PeopleExtractor", message = "Caching response for:\n$text")
                    Napier.d(tag = "PeopleExtractor") { "Response: ${response.trim()}" }
                    generativeAICache.putEntry(documentId, response.trim())
                }
                if (response == null) {
                    return@withContext AIResult.Error(AIError.InvalidResponse)
                }
                val people = response.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { Person(name = it) }
                AIResult.Success(people, fromCache = false)
            } catch (e: Exception) {
                Napier.e(
                    tag = "PeopleExtractor",
                    message = "Failed to extract people",
                    throwable = e
                )
                AIResult.Error(AIError.Unknown, e)
            }
        }
}
