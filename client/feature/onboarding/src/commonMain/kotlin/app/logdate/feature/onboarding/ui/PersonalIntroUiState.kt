package app.logdate.feature.onboarding.ui

/**
 * UI state for the personal introduction onboarding screen.
 * 
 * This screen has a two-step flow: name collection, then bio collection with LLM processing.
 */
data class PersonalIntroUiState(
    val currentStep: PersonalIntroStep = PersonalIntroStep.Name,
    val name: String = "",
    val bio: String = "",
    val nameError: String? = null,
    val bioError: String? = null,
    val isProcessingLlm: Boolean = false,
    val llmResponse: String? = null,
    val llmError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val canContinueFromName: Boolean
        get() = name.trim().isNotEmpty() && nameError == null && !isLoading
    
    val canContinueFromBio: Boolean
        get() = bio.trim().isNotEmpty() && bioError == null && !isProcessingLlm && !isLoading
    
    val canFinish: Boolean
        get() = currentStep == PersonalIntroStep.LlmResponse && llmResponse != null && !isLoading
}

/**
 * Represents the current step in the personal introduction flow.
 */
sealed class PersonalIntroStep {
    /**
     * Step 1: Collecting the user's name.
     */
    data object Name : PersonalIntroStep()
    
    /**
     * Step 2: Collecting the user's bio.
     */
    data object Bio : PersonalIntroStep()
    
    /**
     * Step 3: Showing the LLM's friendly response and allowing the user to continue.
     */
    data object LlmResponse : PersonalIntroStep()
}

/**
 * Data class for the LLM processing request.
 */
data class LlmProcessingRequest(
    val userName: String,
    val userBio: String
)

/**
 * Represents the result of LLM processing.
 */
sealed class LlmProcessingResult {
    data class Success(val response: String) : LlmProcessingResult()
    data class Error(val message: String) : LlmProcessingResult()
}