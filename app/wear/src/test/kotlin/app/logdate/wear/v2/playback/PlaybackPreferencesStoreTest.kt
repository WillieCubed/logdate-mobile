package app.logdate.wear.v2.playback

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals

class PlaybackPreferencesStoreTest {
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences =
        mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getString("speed", null) } returns null
            every { getBoolean("skip_silence", false) } returns false
        }

    @Test
    fun `nothing saved means normal speed with silence`() {
        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        assertEquals(PlaybackSpeed.NORMAL, store.speed.value)
        assertEquals(false, store.skipSilence.value)
    }

    @Test
    fun `saved choices are read back`() {
        every { preferences.getString("speed", null) } returns "1.5"
        every { preferences.getBoolean("skip_silence", false) } returns true

        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        assertEquals(PlaybackSpeed.FASTER, store.speed.value)
        assertEquals(true, store.skipSilence.value)
    }

    @Test
    fun `new choices are saved and reported at once`() {
        val store = SharedPreferencesPlaybackPreferencesStore(preferences)

        store.setSpeed(PlaybackSpeed.FASTEST)
        store.setSkipSilence(true)

        verify { editor.putString("speed", "2") }
        verify { editor.putBoolean("skip_silence", true) }
        assertEquals(PlaybackSpeed.FASTEST, store.speed.value)
        assertEquals(true, store.skipSilence.value)
    }

    @Test
    fun `the speeds step from normal to twice and back`() {
        assertEquals(PlaybackSpeed.FASTER, PlaybackSpeed.NORMAL.next())
        assertEquals(PlaybackSpeed.FASTEST, PlaybackSpeed.FASTER.next())
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.FASTEST.next())
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.fromStored("3"))
    }
}
