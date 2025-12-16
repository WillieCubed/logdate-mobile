package app.logdate.client.intelligence.generativeai.openai

import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAiClientTest {

    @Test
    fun toOpenAiChatMessage_convertsCorrectly() {
        val message = GenerativeAIChatMessage("user", "Hello world")
        val openAiMessage = message.toOpenAiChatMessage()
        
        assertEquals("user", openAiMessage.role)
        assertEquals("Hello world", openAiMessage.content)
    }

    @Test
    fun toOpenAiChatMessage_withSystemRole_convertsCorrectly() {
        val message = GenerativeAIChatMessage("system", "You are a helpful assistant")
        val openAiMessage = message.toOpenAiChatMessage()
        
        assertEquals("system", openAiMessage.role)
        assertEquals("You are a helpful assistant", openAiMessage.content)
    }

    @Test
    fun toOpenAiChatMessage_withAssistantRole_convertsCorrectly() {
        val message = GenerativeAIChatMessage("assistant", "I'm here to help you.")
        val openAiMessage = message.toOpenAiChatMessage()
        
        assertEquals("assistant", openAiMessage.role)
        assertEquals("I'm here to help you.", openAiMessage.content)
    }

    @Test
    fun openAiRequest_serializesCorrectly() {
        val messages = listOf(
            OpenAiChatMessage("system", "You are helpful"),
            OpenAiChatMessage("user", "Hello")
        )
        val request = OpenAiRequest(
            model = "gpt-4.1-mini-2025-04-14",
            messages = messages,
            temperature = 0.7
        )
        
        assertEquals("gpt-4.1-mini-2025-04-14", request.model)
        assertEquals(2, request.messages.size)
        assertEquals(0.7, request.temperature)
        assertEquals("system", request.messages[0].role)
        assertEquals("You are helpful", request.messages[0].content)
        assertEquals("user", request.messages[1].role)
        assertEquals("Hello", request.messages[1].content)
    }

    @Test
    fun openAiResponse_dataClassesHaveCorrectStructure() {
        val choice = Choice(
            message = OpenAiChatMessage("assistant", "Test response"),
            index = 0,
            finish_reason = "stop"
        )
        
        val response = OpenAiResponse(
            id = "chatcmpl-123",
            `object` = "chat.completion",
            created = 1677652288,
            model = "gpt-4.1-mini-2025-04-14",
            choices = listOf(choice)
        )
        
        assertEquals("chatcmpl-123", response.id)
        assertEquals("chat.completion", response.`object`)
        assertEquals(1677652288, response.created)
        assertEquals("gpt-4.1-mini-2025-04-14", response.model)
        assertEquals(1, response.choices.size)
        assertEquals("Test response", response.choices[0].message.content)
        assertEquals("stop", response.choices[0].finish_reason)
    }

    @Test
    fun openAiChatMessage_dataClassWorksCorrectly() {
        val message = OpenAiChatMessage("user", "What is the weather like?")
        
        assertEquals("user", message.role)
        assertEquals("What is the weather like?", message.content)
    }

    @Test
    fun generativeAIChatMessage_convertsToOpenAiFormat() {
        val originalMessage = GenerativeAIChatMessage("system", "Be concise")
        val convertedMessage = originalMessage.toOpenAiChatMessage()
        
        // Test round-trip conversion maintains data
        assertEquals(originalMessage.role, convertedMessage.role)
        assertEquals(originalMessage.content, convertedMessage.content)
    }

    @Test
    fun multipleMessages_convertCorrectly() {
        val messages = listOf(
            GenerativeAIChatMessage("system", "You are a helpful assistant."),
            GenerativeAIChatMessage("user", "Tell me about cats."),
            GenerativeAIChatMessage("assistant", "Cats are fascinating animals.")
        )
        
        val convertedMessages = messages.map { it.toOpenAiChatMessage() }
        
        assertEquals(3, convertedMessages.size)
        assertEquals("system", convertedMessages[0].role)
        assertEquals("You are a helpful assistant.", convertedMessages[0].content)
        assertEquals("user", convertedMessages[1].role)
        assertEquals("Tell me about cats.", convertedMessages[1].content)
        assertEquals("assistant", convertedMessages[2].role)
        assertEquals("Cats are fascinating animals.", convertedMessages[2].content)
    }

    @Test
    fun openAiRequest_withDefaultTemperature_hasCorrectValue() {
        val request = OpenAiRequest(
            model = "gpt-4",
            messages = listOf(OpenAiChatMessage("user", "Hello"))
        )
        
        assertEquals(0.7, request.temperature)
    }

    @Test
    fun openAiRequest_withCustomTemperature_usesCustomValue() {
        val request = OpenAiRequest(
            model = "gpt-4",
            messages = listOf(OpenAiChatMessage("user", "Hello")),
            temperature = 0.1
        )
        
        assertEquals(0.1, request.temperature)
    }
}