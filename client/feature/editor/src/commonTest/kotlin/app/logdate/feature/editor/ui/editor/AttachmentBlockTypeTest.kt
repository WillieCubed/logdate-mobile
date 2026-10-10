package app.logdate.feature.editor.ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentBlockTypeTest {
    @Test
    fun `photos stored by LogDate open as images`() {
        listOf("jpg", "png", "gif", "webp", "heic", "avif").forEach { extension ->
            assertEquals(BlockType.IMAGE, attachmentBlockType("logdate-media://media/ab/cdef.$extension"), extension)
        }
    }

    @Test
    fun `every video format LogDate stores opens as a video`() {
        listOf("mp4", "mov", "webm", "mkv", "3gp", "3g2", "avi", "mpg", "ogv", "wmv").forEach { extension ->
            assertEquals(BlockType.VIDEO, attachmentBlockType("logdate-media://media/ab/cdef.$extension"), extension)
        }
    }

    @Test
    fun `recordings and audio files open as audio`() {
        listOf("m4a", "mp3", "ogg", "aac", "amr", "flac", "opus", "wav", "weba").forEach { extension ->
            assertEquals(BlockType.AUDIO, attachmentBlockType("logdate-media://media/ab/cdef.$extension"), extension)
        }
    }

    @Test
    fun `content URIs without an extension use the media type in their path`() {
        assertEquals(BlockType.VIDEO, attachmentBlockType("content://media/external/video/media/12"))
        assertEquals(BlockType.AUDIO, attachmentBlockType("content://media/external/audio/media/7"))
        assertEquals(BlockType.IMAGE, attachmentBlockType("content://media/external/images/media/3"))
    }

    @Test
    fun `an extension wins over a misleading path`() {
        assertEquals(BlockType.VIDEO, attachmentBlockType("file:///data/files/images/clip.mp4"))
    }
}
