package app.logdate.client.domain.export.archive

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchivePathAllocatorTest {
    private val allocator = ArchivePathAllocator()

    @Test
    fun `the first path with a name is used as asked`() {
        assertEquals("media/photos/a.jpg", allocator.allocate("media/photos", "a", "jpg").value)
    }

    @Test
    fun `a repeated name gets a numeric suffix before the extension`() {
        allocator.allocate("media/photos", "a", "jpg")

        assertEquals("media/photos/a_2.jpg", allocator.allocate("media/photos", "a", "jpg").value)
        assertEquals("media/photos/a_3.jpg", allocator.allocate("media/photos", "a", "jpg").value)
    }

    @Test
    fun `names that differ only by case collide because common file systems ignore case`() {
        allocator.allocate("media/photos", "IMG", "JPG")

        assertEquals("media/photos/img_2.jpg", allocator.allocate("media/photos", "img", "jpg").value)
    }

    @Test
    fun `the same name in another folder is not a collision`() {
        allocator.allocate("media/photos", "a", "jpg")

        assertEquals("media/videos/a.jpg", allocator.allocate("media/videos", "a", "jpg").value)
    }

    @Test
    fun `a reserved fixed path is never handed out`() {
        allocator.reserve(ArchivePath.of("README.txt"))

        assertEquals("readme_2.txt", allocator.allocate("", "readme", "txt").value)
    }

    @Test
    fun `a stem that is too long is shortened so the path stays within budget`() {
        val path = allocator.allocate("media/photos", "x".repeat(200), "jpg")

        assertEquals(ArchivePath.MAX_SEGMENT_LENGTH, path.fileName.length)
        assertEquals("jpg", path.fileName.substringAfterLast('.'))
    }

    @Test
    fun `a shortened stem still gets a suffix when it collides`() {
        val first = allocator.allocate("media/photos", "x".repeat(200), "jpg")
        val second = allocator.allocate("media/photos", "x".repeat(200), "jpg")

        assertEquals(ArchivePath.MAX_SEGMENT_LENGTH, second.fileName.length)
        assertEquals(false, first == second)
        assertEquals("_2.jpg", second.fileName.takeLast(6))
    }
}
