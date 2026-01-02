package app.logdate.client.domain.onboarding

import app.logdate.client.intelligence.generativeai.GenerativeAIChatClient
import app.logdate.client.intelligence.generativeai.GenerativeAIChatMessage
import app.logdate.client.intelligence.generativeai.GenerativeAIRequest
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.shared.model.profile.LogDateProfile
import io.github.aakira.napier.Napier

/**
 * Use case for processing personal introduction during onboarding.
 * 
 * This use case handles the complete flow of collecting user's name and bio,
 * processing it with LLM for a friendly response, and saving to the local profile.
 */
class ProcessPersonalIntroductionUseCase(
    private val profileRepository: ProfileRepository,
    private val generativeAiClient: GenerativeAIChatClient,
    private val networkAvailabilityMonitor: NetworkAvailabilityMonitor
) {
    
    /**
     * Process the personal introduction with name, bio, and LLM response.
     * 
     * @param name The user's display name
     * @param bio The user's bio/self-description
     * @return Result containing the processed introduction data
     */
    suspend operator fun invoke(name: String, bio: String): Result {
        return try {
            Napier.d("Processing personal introduction for user: $name")
            
            // Generate friendly LLM response
            val llmResponse = generateFriendlyResponse(name, bio)
            
            // Save display name to profile
            val nameResult = profileRepository.updateDisplayName(name.trim())
            if (nameResult.isFailure) {
                val exception = nameResult.exceptionOrNull()
                Napier.e("Failed to save display name", exception)
                return Result.Error("Failed to save your name: ${exception?.message}")
            }
            
            // Save bio to profile (both LLM-processed and original)
            val bioResult = profileRepository.updateBio(
                bio = llmResponse,
                originalBio = bio.trim()
            )
            if (bioResult.isFailure) {
                val exception = bioResult.exceptionOrNull()
                Napier.e("Failed to save bio", exception)
                return Result.Error("Failed to save your information: ${exception?.message}")
            }
            
            val updatedProfile = bioResult.getOrNull()
                ?: return Result.Error("Failed to retrieve updated profile")
            
            Napier.d("Personal introduction processed successfully")
            Result.Success(
                ProcessedIntroduction(
                    profile = updatedProfile,
                    llmResponse = llmResponse,
                    originalBio = bio.trim()
                )
            )
            
        } catch (e: Exception) {
            Napier.e("Unexpected error processing personal introduction", e)
            Result.Error("An unexpected error occurred: ${e.message}")
        }
    }
    
    /**
     * Generate a friendly, reflective response using the LLM.
     */
    private suspend fun generateFriendlyResponse(name: String, bio: String): String {
        return try {
            if (!networkAvailabilityMonitor.isNetworkAvailable()) {
                return createFallbackResponse(name)
            }
            val systemMessage = GenerativeAIChatMessage(
                role = "system",
                content = """You are a friendly, warm AI assistant helping someone set up their personal journal app called LogDate. 

Your role is to respond as a supportive friend who's excited to get to know them after they share their name and a bit about themselves.

Guidelines:
- Keep responses to 2-3 sentences maximum
- Be encouraging and personal
- Reflect back what they shared in a warm, genuine way
- Express enthusiasm about their journaling journey
- Don't ask questions - just acknowledge and encourage
- Use their name naturally in your response"""
            )
            
            val userMessage = GenerativeAIChatMessage(
                role = "user", 
                content = "My name is $name and here's a bit about me: $bio"
            )

            val response = generativeAiClient.submit(
                GenerativeAIRequest(
                    messages = listOf(systemMessage, userMessage),
                    model = generativeAiClient.defaultModel
                )
            )
            when (response) {
                is app.logdate.client.intelligence.AIResult.Success -> {
                    response.value.content.takeIf { it.isNotBlank() }
                        ?: createFallbackResponse(name)
                }
                else -> createFallbackResponse(name)
            }
                
        } catch (e: Exception) {
            Napier.w("LLM processing failed, using fallback response", e)
            createFallbackResponse(name)
        }
    }
    
    /**
     * Create a fallback response when LLM processing fails.
     */
    private fun createFallbackResponse(name: String): String {
        return "It's wonderful to meet you, $name! I can tell you have some great stories to share, and I'm excited to help you capture all your memories in LogDate."
    }
    
    /**
     * Result sealed class for the use case operation.
     */
    sealed class Result {
        data class Success(val data: ProcessedIntroduction) : Result()
        data class Error(val message: String) : Result()
    }
    
    /**
     * Data class representing the processed introduction result.
     */
    data class ProcessedIntroduction(
        val profile: LogDateProfile,
        val llmResponse: String,
        val originalBio: String
    )
}
