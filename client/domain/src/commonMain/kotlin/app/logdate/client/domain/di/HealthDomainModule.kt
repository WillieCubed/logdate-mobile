package app.logdate.client.domain.di

import app.logdate.client.domain.timeline.GetDayBoundsUseCase
import org.koin.dsl.module

/**
 * Provides domain layer health-related use cases.
 */
val healthDomainModule = module {
    // Provide use cases that depend on health repositories
    factory { 
        GetDayBoundsUseCase(healthRepository = get())
    }
}