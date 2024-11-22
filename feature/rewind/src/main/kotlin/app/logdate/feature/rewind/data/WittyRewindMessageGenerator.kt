package app.logdate.feature.rewind.data

import javax.inject.Inject

/**
 * Randomly generates a message to accompany a Rewind display.
 */
class WittyRewindMessageGenerator @Inject constructor() : RewindMessageGenerator {
    // TODO: Add more messages
    private companion object {
        private val AVAILABLE_MESSAGES = listOf<String>(
            "Quite the adventurous one, are you?",
        )

        private val UNAVAILABLE_MESSAGES = listOf<String>(
            "Still working on the Rewind. Go touch some grass in the meantime.",
        )
    }

    override suspend fun generateMessage(rewindAvailable: Boolean): String {
        return selectMessage(rewindAvailable)
    }

    private fun selectMessage(rewindAvailable: Boolean): String {
        val messages = if (rewindAvailable) AVAILABLE_MESSAGES else UNAVAILABLE_MESSAGES
        return messages.random()
    }
}