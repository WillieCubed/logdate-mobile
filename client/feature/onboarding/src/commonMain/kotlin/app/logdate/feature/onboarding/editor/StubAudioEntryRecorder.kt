package app.logdate.feature.onboarding.editor

object StubAudioEntryRecorder : AudioEntryRecorder {
    override val isRecording: Boolean
        get() = false

    override fun startRecording(outputFilename: String) {
        TODO("Not yet implemented")
    }

    override fun stopRecording() {
        TODO("Not yet implemented")
    }

    override fun cancelRecording() {
        TODO("Not yet implemented")
    }
}