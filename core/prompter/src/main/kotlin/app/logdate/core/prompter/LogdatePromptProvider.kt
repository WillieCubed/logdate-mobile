package app.logdate.core.prompter

import javax.inject.Inject

class LogdatePromptProvider @Inject constructor(
    private val localPromptDataSource: LocalPromptDataSource,
    private val remotePromptDataSource: RemotePromptDataSource
) : PromptProvider {
    override suspend fun getPromptById(promptId: String): PromptData {
        TODO("Not yet implemented")
    }

    override suspend fun getRandomPrompts(count: Int): PromptData {
        TODO("Not yet implemented")
    }

    override suspend fun getTodaysPrompts(): List<PromptData> {
        TODO("Not yet implemented")
    }

    override suspend fun getPromptEvents(): List<PromptEvent> {
        TODO("Not yet implemented")
    }
}

class LocalPromptDataSource @Inject constructor() {
}

class RemotePromptDataSource @Inject constructor() {
}