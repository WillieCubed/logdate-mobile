package app.logdate.client.intelligence.generativeai

/**
 * A generic interface for a chat client that uses a generative AI model to generate responses.
 */
interface GenerativeAIChatClient {
    /**
     * Sends a list of prompts to the generative AI model and returns the generated response.
     *
     * @param prompts The list of prompts to send to the model.
     * @return The generated response, or null if no response was generated.
     */
    suspend fun submit(prompts: List<GenerativeAIChatMessage>): String?

    suspend fun submit(vararg prompts: GenerativeAIChatMessage): String? = submit(prompts.toList())
}