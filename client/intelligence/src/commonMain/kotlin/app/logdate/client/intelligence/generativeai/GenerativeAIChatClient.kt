package app.logdate.client.intelligence.generativeai

import app.logdate.client.intelligence.AIResult

/**
 * A generic interface for a chat client that uses a generative AI model to generate responses.
 */
interface GenerativeAIChatClient {
    /**
     * Sends a request to the generative AI model and returns the generated response.
     */
    suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse>

    @Deprecated("Use submit(GenerativeAIRequest) for structured error handling.")
    suspend fun submit(prompts: List<GenerativeAIChatMessage>): String? =
        when (val result = submit(GenerativeAIRequest(messages = prompts))) {
            is AIResult.Success -> result.value.content
            else -> null
        }

    @Deprecated("Use submit(GenerativeAIRequest) for structured error handling.")
    suspend fun submit(vararg prompts: GenerativeAIChatMessage): String? =
        when (val result = submit(GenerativeAIRequest(messages = prompts.toList()))) {
            is AIResult.Success -> result.value.content
            else -> null
        }
}
