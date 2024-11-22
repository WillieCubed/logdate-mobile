package app.logdate.core.intelligence.generativeai.openai

import app.logdate.core.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.core.intelligence.generativeai.GenerativeAIChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import javax.inject.Singleton

/**
 * A generative AI client that uses the OpenAI API to generate responses.
 */
@Singleton
class OpenAiClient(
    private val apiKey: String,
    private val httpClient: HttpClient,
) : GenerativeAIChatClient {

    override suspend fun submit(prompts: List<GenerativeAIChatMessage>): String? {
        val response: HttpResponse =
            httpClient.post("https://api.openai.com/v1/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
                setBody(
                    OpenAiRequest(
                        model = "gpt-4o-mini",
                        messages = prompts.map(GenerativeAIChatMessage::toOpenAiChatMessage),
                    )
                )
            }
        val openAiResponse: OpenAiResponse = response.body()
        return openAiResponse.choices.firstOrNull()?.message?.content?.trim()
    }
}

@OptIn(InternalSerializationApi::class)
@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.7,
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String,
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OpenAiResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class Choice(
    val message: OpenAiChatMessage,
    val index: Int,
    val finish_reason: String,
)

internal fun GenerativeAIChatMessage.toOpenAiChatMessage() =
    OpenAiChatMessage(role, content)