package app.logdate.client.intelligence.rewind

interface RewindMessageGenerator {
    /**
     * Generates a generic greeting message for Rewind displays.
     *
     * @param rewindAvailable Whether the user has a rewind available.
     */
    suspend fun generateMessage(rewindAvailable: Boolean): String

    /**
     * Generates a contextual message based on rewind content characteristics.
     *
     * Falls back to [generateMessage] behavior when no contextual signals are provided.
     *
     * @param rewindAvailable Whether the rewind is ready
     * @param photoCount Number of photos in the rewind
     * @param textCount Number of text entries in the rewind
     * @param peopleCount Number of people mentioned
     * @param themes Narrative themes from AI analysis
     */
    suspend fun generateContextualMessage(
        rewindAvailable: Boolean,
        photoCount: Int = 0,
        textCount: Int = 0,
        peopleCount: Int = 0,
        themes: List<String> = emptyList(),
    ): String = generateMessage(rewindAvailable)
}
