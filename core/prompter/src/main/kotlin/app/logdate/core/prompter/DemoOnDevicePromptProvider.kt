package app.logdate.core.prompter

import javax.inject.Inject

class DemoOnDevicePromptProvider @Inject constructor() : PromptProvider {

    private companion object {
        private val TEST_PROMPTS = listOf(
            PromptData(
                promptId = "1",
                promptText = "What did you do today?",
                supportedResponses = listOf(ResponseType.TEXT, ResponseType.IMAGE, ResponseType.VIDEO, ResponseType.AUDIO),
                responseLimit = 1,
            ),
            PromptData(
                promptId = "2",
                promptText = "What was the best moment of your day?",
                supportedResponses = listOf(ResponseType.TEXT, ResponseType.IMAGE, ResponseType.VIDEO),
                responseLimit = 1,
            ),
            PromptData(
                promptId = "3",
                promptText = "Who is someone you miss right now?",
                supportedResponses = listOf(ResponseType.TEXT, ResponseType.IMAGE),
                responseLimit = 1,
            )
        )
    }

    override suspend fun getPromptById(promptId: String): PromptData {
        return TEST_PROMPTS.first { it.promptId == promptId }
    }

    override suspend fun getRandomPrompts(count: Int): PromptData {
        return TEST_PROMPTS.random()
    }

    override suspend fun getTodaysPrompts(): List<PromptData> {
        return TEST_PROMPTS
    }

    override suspend fun getPromptEvents(): List<PromptEvent> {
        return emptyList()
    }
}