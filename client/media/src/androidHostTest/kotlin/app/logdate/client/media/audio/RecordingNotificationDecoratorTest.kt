package app.logdate.client.media.audio

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class RecordingNotificationDecoratorTest {
    private val decorator = RecordingNotificationDecorator { _, _, _, _ -> }

    @Test
    fun `an app that supplies a decorator has it applied`() {
        val application =
            object : RecordingNotificationDecorator.Provider {
                override val recordingNotificationDecorator = decorator
            }

        assertSame(decorator, recordingNotificationDecoratorOf(application))
    }

    @Test
    fun `an app that supplies none gets the plain notification`() {
        assertNull(recordingNotificationDecoratorOf(Any()))
        assertNull(recordingNotificationDecoratorOf(null))
    }
}
