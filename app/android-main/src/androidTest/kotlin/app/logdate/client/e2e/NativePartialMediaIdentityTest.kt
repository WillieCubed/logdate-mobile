package app.logdate.client.e2e

import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativePartialMediaIdentityTest {
    private val files =
        MediaFileResolver(
            object : MediaDirectories {
                override fun directory(collection: MediaCollection): String =
                    when (collection) {
                        MediaCollection.Library -> "$ROOT/media"
                        MediaCollection.Recordings -> "$ROOT/audio_notes"
                    }
            },
            fileExists = { false },
        )

    @Test
    fun missingLegacyOriginalMatchesItsOmittedOwnedIdentityWithoutChangingStoredReference() {
        val original = "file:$ROOT/media/objects/sha256/aa/${"a".repeat(64)}.png"
        val expected = "logdate-media://library/objects/sha256/aa/${"a".repeat(64)}.png"
        val unavailable = UnavailableMediaReferences.nativeRestore(original)
        assertEquals(expected, nativePartialMediaIdentity(unavailable, files))
        assertEquals(expected, nativePartialMediaIdentity(expected, files))
        assertEquals(original, files.storedReference(original))
        assertEquals(original, UnavailableMediaReferences.nativeSource(unavailable))
    }

    @Test
    fun providerAndForeignPrivateReferencesCannotMatchTheOmittedOriginal() {
        val expected = "logdate-media://library/photo.png"
        listOf("content://provider/photo.png", "file:/data/user/0/unrelated.app/files/media/photo.png").forEach { original ->
            assertEquals(original, nativePartialMediaIdentity(UnavailableMediaReferences.nativeRestore(original), files))
            assertNotEquals(expected, nativePartialMediaIdentity(original, files))
        }
    }

    private companion object {
        const val ROOT = "/data/user/0/studio.hypertext.logdate.debug/files"
    }
}
