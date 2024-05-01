package app.logdate.core.prompter

import kotlinx.datetime.Instant

/**
 * A service that allows for syncing data between the local database and a remote server.
 */
interface PromptProvider {

    /**
     * Retrieves a prompt by its ID.
     *
     * @param promptId The ID of the prompt to retrieve.
     *
     * @return The prompt data.
     *
     * @throws IllegalArgumentException If the prompt ID is not valid.
     */
    @Throws(IllegalArgumentException::class)
    suspend fun getPromptById(promptId: String): PromptData

    suspend fun getRandomPrompts(count: Int = 1): PromptData

    suspend fun getTodaysPrompts(): List<PromptData>

    suspend fun getPromptEvents(): List<PromptEvent>
}

data class PromptEvent(
    /**
     * The ID of this event.
     */
    val eventId: String,
    /**
     * A description of the event that this prompt is associated with.
     */
    val purpose: String,
    /**
     * The ID of the prompt that this event is associated with.
     */
    val startTime: Instant,
    /**
     * When the contents of this prompt should be revealed to users.
     */
    val revealTime: Instant,
    /**
     * When this prompt should not be shown to users.
     */
    val endTime: Instant,
    /**
     * Whether the contents of responses to this prompt are private to others besides the user.
     */
    val isSecret: Boolean,
)

data class PromptData(
    val promptId: String,
    val promptText: String,
    val supportedResponses: List<ResponseType> = listOf(ResponseType.TEXT),
    val responseLimit: Int = 1,
)

enum class ResponseMode {
    STATUS, POST,
}

enum class ResponseType {
    TEXT, IMAGE, VIDEO, AUDIO,
}
