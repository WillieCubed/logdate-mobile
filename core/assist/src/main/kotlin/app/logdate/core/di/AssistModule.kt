package app.logdate.core.di

import app.logdate.core.assist.AndroidAssistantActionsProvider
import app.logdate.core.assist.AndroidAssistantContextProvider
import app.logdate.core.assist.AssistantActionsProvider
import app.logdate.core.assist.AssistantContextProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistModule {
    @Binds
    abstract fun bindAssistantContextProvider(assistantContextProvider: AndroidAssistantContextProvider): AssistantContextProvider

    @Binds
    abstract fun bindAssistantActionsProvider(assistantActionsProvider: AndroidAssistantActionsProvider): AssistantActionsProvider
}