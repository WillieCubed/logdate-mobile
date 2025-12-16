package app.logdate.client.intelligence.fakes

import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import kotlinx.coroutines.delay

/**
 * Fake implementation of GenerativeAIChatClient for testing.
 * 
 * This allows testing of AI-dependent functionality without actual API calls.
 */
class FakeGenerativeAIChatClient : GenerativeAIChatClient {
    
    // Track all submissions for testing
    var submissions = mutableListOf<List<GenerativeAIChatMessage>>()
    
    // Configurable responses for different test scenarios
    var responses = mutableMapOf<String, String>()
    var defaultResponse: String? = "Default AI response"
    
    // Error simulation
    var shouldThrowError = false
    var errorToThrow: Exception = Exception("AI client error")
    
    // Delay simulation
    var delayMs: Long = 0
    
    override suspend fun submit(prompts: List<GenerativeAIChatMessage>): String? {
        submissions.add(prompts.toList())
        
        if (delayMs > 0) {
            delay(delayMs)
        }
        
        if (shouldThrowError) {
            throw errorToThrow
        }
        
        // Look for specific responses based on user content
        val userMessage = prompts.find { it.role == "user" }?.content
        if (userMessage != null && responses.containsKey(userMessage)) {
            return responses[userMessage]
        }
        
        return defaultResponse
    }
    
    fun clear() {
        submissions.clear()
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