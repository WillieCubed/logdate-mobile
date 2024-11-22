package app.logdate.core.intelligence.entity.people

import app.logdate.core.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.core.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.model.Person
import jakarta.inject.Inject
import kotlin.uuid.ExperimentalUuidApi

/**
 * A utility to extract people's names from text.
 */
@OptIn(ExperimentalUuidApi::class)
class PeopleExtractor @Inject constructor(
    private val generativeAIChatClient: GenerativeAIChatClient,
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
     * @param text The text to extract people's names from.
     * @return A list of people's names extracted from the text.
     */
    suspend fun extractPeople(text: String): List<Person> {
        val prompts = listOf(
            GenerativeAIChatMessage("system", EXTRACTION_PROMPT),
            GenerativeAIChatMessage("user", text),
            // TODO: Use structured response format
        )
        val response = generativeAIChatClient.submit(prompts)
        return response?.split("\n")?.map { Person(name = it) } ?: emptyList()
    }
}