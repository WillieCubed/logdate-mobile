package app.logdate.client.domain.di

import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.account.HasLogDateCloudAccountUseCase
import app.logdate.client.domain.user.GetUserIdUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Dedicated module for account-related dependencies to avoid circular dependencies
 */
val accountModule: Module = module {
    // Account
    factory { CreatePasskeyAccountUseCase(get()) }
    factory { CreatePasskeyUseCase(get()) }
    factory { GetCurrentAccountUseCase(get()) }
    factory { HasLogDateCloudAccountUseCase(get()) }
    factory { DeletePasskeyUseCase(get()) }
    
    // User identity 
    factory { GetUserIdUseCase(get(), get()) }
}