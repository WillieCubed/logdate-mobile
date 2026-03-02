package app.logdate.client.intelligence.generativeai

import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.AIUnavailableReason

/**
 * A no-op implementation of GenerativeAIChatClient used when API credentials are unavailable.
 *
 * This prevents crashes and allows graceful degradation of AI features when the OpenAI API key
 * is not configured. All operations return AIResult.Unavailable(MissingCredentials).
 */
internal class NoOpGenerativeAIChatClient : GenerativeAIChatClient {
    override val providerId: String = "noop"
    override val defaultModel: String? = null

    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> =
        AIResult.Unavailable(AIUnavailableReason.MissingCredentials)
}
