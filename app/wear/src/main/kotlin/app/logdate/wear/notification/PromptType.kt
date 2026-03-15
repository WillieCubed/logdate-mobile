package app.logdate.wear.notification

/**
 * Types of contextual journal prompts the watch can send.
 */
enum class PromptType {
    MORNING,
    EVENING,
    NONE;

    companion object {
        private val MORNING_HOURS = 6..11
        private val EVENING_HOURS = 20..23

        /**
         * Returns the appropriate prompt type for the given hour of day (0-23).
         */
        fun forHour(hour: Int): PromptType = when (hour) {
            in MORNING_HOURS -> MORNING
            in EVENING_HOURS -> EVENING
            else -> NONE
        }
    }
}

/**
 * Content for a contextual notification prompt.
 */
data class PromptContent(
    val title: String,
    val body: String,
) {
    companion object {
        fun forType(type: PromptType): PromptContent = when (type) {
            PromptType.MORNING -> PromptContent(
                title = "Good morning!",
                body = "How are you feeling today?",
            )
            PromptType.EVENING -> PromptContent(
                title = "Time to reflect",
                body = "How was your day?",
            )
            PromptType.NONE -> PromptContent(title = "", body = "")
        }
    }
}

/**
 * Tracks prompt cooldowns to prevent duplicate notifications per day.
 *
 * Each prompt type is allowed once per day epoch (days since epoch).
 */
class PromptCooldownTracker {
    private val sentPrompts = mutableMapOf<PromptType, Long>()

    fun markSent(type: PromptType, dayEpoch: Long) {
        sentPrompts[type] = dayEpoch
    }

    fun canSend(type: PromptType, dayEpoch: Long): Boolean {
        val lastSentDay = sentPrompts[type] ?: return true
        return dayEpoch > lastSentDay
    }
}
