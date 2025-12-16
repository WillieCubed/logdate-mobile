package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.onboarding.ProcessPersonalIntroductionUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the personal introduction onboarding screen.
 * 
 * Manages the two-step flow: name collection, bio collection, and LLM processing
 * to create a friendly, personal introduction experience.
 */
class PersonalIntroViewModel(
    private val processPersonalIntroductionUseCase: ProcessPersonalIntroductionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalIntroUiState())
    val uiState: StateFlow<PersonalIntroUiState> = _uiState.asStateFlow()

    /**
     * Update the user's name input.
     */
    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null // Clear error when user starts typing
        )
    }

    /**
     * Update the user's bio input.
     */
    fun onBioChanged(bio: String) {
        _uiState.value = _uiState.value.copy(
            bio = bio,
            bioError = null // Clear error when user starts typing
        )
    }

    /**
     * Proceed from name step to bio step.
     */
    fun proceedToBio() {
        val currentState = _uiState.value
        
        if (currentState.name.trim().isEmpty()) {
            _uiState.value = currentState.copy(nameError = "Please enter your name")
            return
        }
        
        _uiState.value = currentState.copy(
            currentStep = PersonalIntroStep.Bio,
            nameError = null
        )
    }

    /**
     * Go back from bio step to name step.
     */
    fun goBackToName() {
        _uiState.value = _uiState.value.copy(
            currentStep = PersonalIntroStep.Name,
            bioError = null,
            llmError = null
        )
    }

    /**
     * Process the bio with LLM and complete the entire introduction flow.
     */
    fun processWithLlm() {
        val currentState = _uiState.value
        
        if (currentState.bio.trim().isEmpty()) {
            _uiState.value = currentState.copy(bioError = "Please tell us a bit about yourself")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = currentState.copy(
                    isProcessingLlm = true,
                    bioError = null,
                    llmError = null
                )
                
                // Process the entire introduction using the domain use case
                val result = processPersonalIntroductionUseCase(
                    name = currentState.name.trim(),
                    bio = currentState.bio.trim()
                )
                
                when (result) {
                    is ProcessPersonalIntroductionUseCase.Result.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessingLlm = false,
                            llmResponse = result.data.llmResponse,
                            currentStep = PersonalIntroStep.LlmResponse
                        )
                        Napier.d("Personal introduction processed successfully")
                    }
                    
                    is ProcessPersonalIntroductionUseCase.Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessingLlm = false,
                            errorMessage = result.message
                        )
                    }
                }
                
            } catch (e: Exception) {
                Napier.e("Failed to process personal introduction", e)
                _uiState.value = _uiState.value.copy(
                    isProcessingLlm = false,
                    errorMessage = "An unexpected error occurred: ${e.message}"
                )
            }
        }
    }

    /**
     * Complete the introduction - this is now handled by processWithLlm().
     * This method is kept for backward compatibility with the UI.
     */
    fun completeIntroduction() {
        // Introduction is already completed in processWithLlm()
        // This method is kept for UI compatibility but doesn't need to do anything
        Napier.d("Introduction already completed")
    }

    /**
     * Clear error messages.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}