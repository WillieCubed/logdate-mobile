package app.logdate.core.di

import app.logdate.core.billing.DefaultSubscriptionBiller
import app.logdate.core.billing.SubscriptionBiller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    abstract fun bindSubscriptionBiller(biller: DefaultSubscriptionBiller): SubscriptionBiller
}