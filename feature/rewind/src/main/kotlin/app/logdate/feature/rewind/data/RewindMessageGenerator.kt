package app.logdate.feature.rewind.data

interface RewindMessageGenerator {
    /**
     * Generates a title message for Rewind displays.
     *
     * The messages are suitable for greetings to delight the user.
     *
     * @param rewindAvailable Whether the user has a rewind available. This is used to determine the
     * message to display.
     */
    suspend fun generateMessage(rewindAvailable: Boolean): String
    // TODO: Use on-device ML to generate unique messages
}