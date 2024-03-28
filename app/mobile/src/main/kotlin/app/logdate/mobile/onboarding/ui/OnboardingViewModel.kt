package app.logdate.mobile.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.user.UserStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userStateRepository: UserStateRepository,
) : ViewModel() {
    fun finishOnboarding(onFinish: () -> Unit) {
        viewModelScope.launch {
            userStateRepository.setIsOnboardingComplete(true)
        }
        onFinish()
    }
}