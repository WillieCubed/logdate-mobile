package app.logdate.feature.core.account.di

import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.feature.core.account.ui.AccountOnboardingViewModel
import app.logdate.feature.core.account.ui.PasskeyCreationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for account feature dependencies.
 *
 * This module provides the ViewModels needed for the cloud account setup flow.
 */
val accountFeatureModule = module {
    // Consolidated ViewModel for all screens except passkey creation
    viewModel { 
        AccountOnboardingViewModel(
            checkUsernameAvailabilityUseCase = get(),
            getAccountSetupDataUseCase = get()
        ) 
    }
    
    // PasskeyCreationViewModel kept separate due to unique functionality
    viewModel { 
        PasskeyCreationViewModel(
            createPasskeyAccountUseCase = get(),
            createRemoteAccountUseCase = get<CreateRemoteAccountUseCase>(),
            getAccountSetupDataUseCase = get()
        ) 
    }
}