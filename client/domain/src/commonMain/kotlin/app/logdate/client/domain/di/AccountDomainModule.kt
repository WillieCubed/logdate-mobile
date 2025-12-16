package app.logdate.client.domain.di

import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.account.HasLogDateCloudAccountUseCase
import app.logdate.client.domain.user.GetUserIdUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module for account-related domain use cases.
 * Separated to avoid circular dependencies with data modules.
 */
val accountDomainModule: Module = module {
    // Account
    factory { CreatePasskeyAccountUseCase(get()) }
    factory { CreatePasskeyUseCase(get()) }
    factory { GetCurrentAccountUseCase(get()) }
    factory { HasLogDateCloudAccountUseCase(get()) }
    factory { DeletePasskeyUseCase(get()) }
    factory { GetAccountSetupDataUseCase(get()) }
    factory { CreateRemoteAccountUseCase(get()) }
    factory { CheckUsernameAvailabilityUseCase(get()) }
    
    // User ID - depends on account
    factory { GetUserIdUseCase(get(), get()) }
}