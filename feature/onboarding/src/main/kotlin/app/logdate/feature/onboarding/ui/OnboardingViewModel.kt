package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import app.logdate.core.data.LibraryContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    repository: LibraryContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun addEntry() {

    }
}
