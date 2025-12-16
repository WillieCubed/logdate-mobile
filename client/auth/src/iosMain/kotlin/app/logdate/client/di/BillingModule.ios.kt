package app.logdate.client.di

import app.logdate.client.billing.StubSubscriptionBiller
import app.logdate.client.billing.SubscriptionBiller
import org.koin.core.module.Module
import org.koin.dsl.module

actual val billingModule: Module = module {
    // TODO: Implement billing on iOS
    single<SubscriptionBiller> { StubSubscriptionBiller() }
}