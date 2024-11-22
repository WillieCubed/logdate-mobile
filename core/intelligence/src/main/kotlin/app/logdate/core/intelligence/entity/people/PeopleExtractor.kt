package app.logdate.core.intelligence.entity.people

import android.util.Log
import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.data.timeline.cache.GenerativeAICache
import app.logdate.core.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.core.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.model.Person
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

/**
 * A utility to extract people's names from text.
 */
@OptIn(ExperimentalUuidApi::class)
class PeopleExtractor @Inject constructor(
    private val generativeAICache: GenerativeAICache,
    private val generativeAIChatClient: GenerativeAIChatClient,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val EXTRACTION_PROMPT = """
You are a system utility that extracts the names of people mentioned in text.
Only literally return the names from the text. Each name must be separated by a new
line. Include references to noun-adjective parings that could be names.
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
    ): List<Person> =
        withContext(ioDispatcher) {
            if (useCached) {
                val cachedResponse = generativeAICache.getEntry(documentId)
                if (cachedResponse != null) {
                    return@withContext cachedResponse.content.split("\n").map { Person(name = it) }
                }
            }
            val prompts = listOf(
                GenerativeAIChatMessage("system", EXTRACTION_PROMPT),
                GenerativeAIChatMessage("user", text),
                // TODO: Use structured response format
            )
            val response = generativeAIChatClient.submit(prompts)
            if (response != null) {
                Log.d("PeopleExtractor", "Caching response for:\n$text")
                generativeAICache.putEntry(documentId, response)
            }
            response?.split("\n")?.map { Person(name = it) } ?: emptyList()
        }
}