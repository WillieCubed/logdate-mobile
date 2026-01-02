package app.logdate.client.intelligence.fakes

import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.intelligence.generativeai.GenerativeAIResponse
import kotlinx.coroutines.delay

/**
 * Fake implementation of GenerativeAIChatClient for testing.
 * 
 * This allows testing of AI-dependent functionality without actual API calls.
 */
class FakeGenerativeAIChatClient : GenerativeAIChatClient {
    override val providerId: String = "fake"
    override val defaultModel: String? = "fake-model"
    
    // Track all submissions for testing
    var submissions = mutableListOf<List<GenerativeAIChatMessage>>()
    var requests = mutableListOf<GenerativeAIRequest>()
    
    // Configurable responses for different test scenarios
    var responses = mutableMapOf<String, String>()
    var defaultResponse: String? = "Default AI response"
    
    // Error simulation
    var shouldThrowError = false
    var errorToThrow: Exception = Exception("AI client error")
    
    // Delay simulation
    var delayMs: Long = 0
    
    override suspend fun submit(request: GenerativeAIRequest): AIResult<GenerativeAIResponse> {
        requests.add(request)
        submissions.add(request.messages.toList())
        
        if (delayMs > 0) {
            delay(delayMs)
        }
        
        if (shouldThrowError) {
            return AIResult.Error(AIError.Unknown, errorToThrow)
        }
        
        // Look for specific responses based on user content
        val userMessage = request.messages.find { it.role == "user" }?.content
        if (userMessage != null && responses.containsKey(userMessage)) {
            return AIResult.Success(GenerativeAIResponse(responses[userMessage] ?: ""))
        }
        
        return if (defaultResponse == null) {
            AIResult.Error(AIError.InvalidResponse)
        } else {
            AIResult.Success(GenerativeAIResponse(defaultResponse ?: ""))
        }
    }
    
    fun clear() {
        submissions.clear()
        requests.clear()
        responses.clear()
        defaultResponse = "Default AI response"
        shouldThrowError = false
        delayMs = 0
    }
    
    fun setResponseFor(userInput: String, response: String) {
        responses[userInput] = response
    }
    
    fun getLastSubmission(): List<GenerativeAIChatMessage>? = submissions.lastOrNull()
    
    fun getLastUserMessage(): String? = 
        submissions.lastOrNull()?.find { it.role == "user" }?.content
    
    fun getLastSystemMessage(): String? = 
        submissions.lastOrNull()?.find { it.role == "system" }?.content
}
